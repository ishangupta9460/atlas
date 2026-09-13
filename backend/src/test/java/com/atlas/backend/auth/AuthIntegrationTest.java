package com.atlas.backend.auth;

import com.atlas.backend.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
