package com.atlas.backend.auth;

import com.atlas.backend.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class AuthIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("Register user successfully — returns 201 without exposing password or hash")
    void registerUserSuccessfully() throws Exception {
        RegisterRequest request = new RegisterRequest("user@example.com", "securePassword123");

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.email", is("user@example.com")))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        assertFalse(responseContent.contains("securePassword123"));
    }

    @Test
    @DisplayName("Register with short password fails — 400 Bad Request")
    void registerShortPasswordFails() throws Exception {
        RegisterRequest request = new RegisterRequest("user@example.com", "short");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code", is("VALIDATION_ERROR")));
    }

    @Test
    @DisplayName("Register with email over database length fails validation, not EMAIL_TAKEN")
    void registerEmailOverDatabaseLengthFailsValidation() throws Exception {
        String oversizedEmail = "a".repeat(246) + "@example.com";
        RegisterRequest request = new RegisterRequest(oversizedEmail, "securePassword123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code", is("VALIDATION_ERROR")));

        assertTrue(userRepository.findByEmail(oversizedEmail).isEmpty());
    }

    @Test
    @DisplayName("Register duplicate email fails — 409 Conflict")
    void registerDuplicateEmailFails() throws Exception {
        RegisterRequest request = new RegisterRequest("user@example.com", "securePassword123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code", is("EMAIL_TAKEN")));
    }

    @Test
    @DisplayName("Concurrent registration for one email returns only created or EMAIL_TAKEN responses")
    void concurrentDuplicateRegistrationDoesNotReturnServerError() throws Exception {
        String requestBody = objectMapper.writeValueAsString(
                new RegisterRequest("concurrent@example.com", "securePassword123"));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<Integer> first = executor.submit(() -> registerConcurrently(requestBody, ready, start));
            Future<Integer> second = executor.submit(() -> registerConcurrently(requestBody, ready, start));

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            List<Integer> statuses = List.of(
                    first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertEquals(1, statuses.stream().filter(status -> status == 201).count());
            assertEquals(1, statuses.stream().filter(status -> status == 409).count());
        } finally {
            executor.shutdownNow();
        }
    }

    private int registerConcurrently(String requestBody, CountDownLatch ready, CountDownLatch start)
            throws Exception {
        ready.countDown();
        assertTrue(start.await(5, TimeUnit.SECONDS));
        return mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    @Test
    @DisplayName("Login with valid credentials returns 200 and token")
    void loginSuccess() throws Exception {
        RegisterRequest reg = new RegisterRequest("user@example.com", "securePassword123");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated());

        LoginRequest login = new LoginRequest("user@example.com", "securePassword123");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.token", not(emptyString())));
    }

    @Test
    @DisplayName("Login with invalid password returns 401 Unauthorized")
    void loginWrongPasswordFails() throws Exception {
        RegisterRequest reg = new RegisterRequest("user@example.com", "securePassword123");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated());

        LoginRequest login = new LoginRequest("user@example.com", "wrongPassword");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code", is("INVALID_CREDENTIALS")));
    }

    @Test
    @DisplayName("Login with non-existent email returns identical 401 Unauthorized")
    void loginNonExistentUserFails() throws Exception {
        LoginRequest login = new LoginRequest("unknown@example.com", "somePassword");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code", is("INVALID_CREDENTIALS")));
    }

    @Test
    @DisplayName("Access GET /api/auth/me with valid token succeeds — 200 OK")
    void meEndpointWithValidToken() throws Exception {
        RegisterRequest reg = new RegisterRequest("user@example.com", "securePassword123");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated());

        LoginRequest login = new LoginRequest("user@example.com", "securePassword123");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();

        AuthResponse authResponse = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(), AuthResponse.class);

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + authResponse.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("user@example.com")))
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/auth/me always returns the identity from the supplied JWT")
    void meEndpointIsIsolatedBetweenAuthenticatedUsers() throws Exception {
        AuthResponse userAToken = registerAndLogin("user-a@example.com");
        AuthResponse userBToken = registerAndLogin("user-b@example.com");

        UserResponse userA = currentUser(userAToken.token());
        UserResponse userB = currentUser(userBToken.token());

        assertEquals("user-a@example.com", userA.email());
        assertEquals("user-b@example.com", userB.email());
        assertFalse(userA.id().equals(userB.id()));

        MvcResult userARequestWithUserBId = mockMvc.perform(get("/api/auth/me")
                        .param("userId", userB.id().toString())
                        .header("Authorization", "Bearer " + userAToken.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(userA.id().intValue())))
                .andExpect(jsonPath("$.email", is("user-a@example.com")))
                .andReturn();

        UserResponse response = objectMapper.readValue(
                userARequestWithUserBId.getResponse().getContentAsString(), UserResponse.class);
        assertEquals(userA, response);
    }

    private AuthResponse registerAndLogin(String email) throws Exception {
        RegisterRequest registration = new RegisterRequest(email, "securePassword123");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registration)))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(email, "securePassword123"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(loginResult.getResponse().getContentAsString(), AuthResponse.class);
    }

    private UserResponse currentUser(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), UserResponse.class);
    }

    @Test
    @DisplayName("Access GET /api/auth/me without token returns 401 Unauthorized")
    void meEndpointWithoutTokenFails() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code", is("UNAUTHORIZED")));
    }

    @Test
    @DisplayName("Access GET /api/auth/me with invalid token returns 401 Unauthorized")
    void meEndpointWithInvalidTokenFails() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code", is("UNAUTHORIZED")));
    }
}
