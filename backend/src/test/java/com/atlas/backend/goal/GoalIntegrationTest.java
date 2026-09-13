package com.atlas.backend.goal;

import com.atlas.backend.event.EventRepository;
import com.atlas.backend.task.TaskRepository;
import com.atlas.backend.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class GoalIntegrationTest {
    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private GoalRepository goalRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private GoalService goalService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach void setUp() {
        cleanDatabase();
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @AfterEach void tearDown() { cleanDatabase(); }

    @Test
    void createFetchAndPatchFollowContractWithoutChangingStates() throws Exception {
        String token = registerAndLogin("goal-owner@example.com");
        long id = createGoal(token, "Learn Spring");

        mockMvc.perform(get("/goals/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lifecycleState", is("active")))
                .andExpect(jsonPath("$.planningState", is("active")));
        mockMvc.perform(patch("/goals/{id}", id).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Learn Spring Boot\",\"targetDeadline\":\"2026-10-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Learn Spring Boot")))
                .andExpect(jsonPath("$.targetDeadline", is("2026-10-01")))
                .andExpect(jsonPath("$.lifecycleState", is("active")))
                .andExpect(jsonPath("$.planningState", is("active")));
        mockMvc.perform(post("/goals").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"  \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error_code", is("VALIDATION_ERROR")));
    }

    @Test
    void endpointTransitionsWriteCorrectEventsAndDoNotCrossStateAxes() throws Exception {
        String token = registerAndLogin("transition-owner@example.com");
        long abandonable = createGoal(token, "Abandon me");
        mockMvc.perform(post("/goals/{id}/abandon", abandonable).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lifecycleState", is("abandoned")))
                .andExpect(jsonPath("$.planningState", is("active")));
        assertEquals("goal.abandoned", eventsFor(abandonable).get(0).getType());

        long risky = createGoal(token, "Risky");
        goalService.markAtRisk(ownerId(token), risky);
        mockMvc.perform(post("/goals/{id}/risk-response", risky).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.planningState", is("active")))
                .andExpect(jsonPath("$.lifecycleState", is("active")));
        assertEquals("goal.risk_resolved", eventsFor(risky).get(1).getType());
        goalService.markAtRisk(ownerId(token), risky);
        mockMvc.perform(post("/goals/{id}/pause", risky).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.planningState", is("paused")));
        assertEquals("goal.paused", eventsFor(risky).get(3).getType());
    }

    @Test
    void everyGoalRouteRejectsForeignOwnershipAndInvalidStateUsesStandardError() throws Exception {
        String alice = registerAndLogin("alice-goal@example.com");
        String bob = registerAndLogin("bob-goal@example.com");
        long goal = createGoal(alice, "Alice private goal");

        mockMvc.perform(get("/goals/{id}", goal).header("Authorization", "Bearer " + bob))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error_code", is("GOAL_NOT_FOUND")));
        mockMvc.perform(patch("/goals/{id}", goal).header("Authorization", "Bearer " + bob)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Nope\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error_code", is("GOAL_NOT_FOUND")));
        mockMvc.perform(post("/goals/{id}/pause", goal).header("Authorization", "Bearer " + bob))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error_code", is("GOAL_NOT_FOUND")));
        mockMvc.perform(post("/goals/{id}/abandon", goal).header("Authorization", "Bearer " + bob))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error_code", is("GOAL_NOT_FOUND")));
        mockMvc.perform(post("/goals/{id}/risk-response", goal).header("Authorization", "Bearer " + bob))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error_code", is("GOAL_NOT_FOUND")));
        mockMvc.perform(post("/goals/{id}/pause", goal).header("Authorization", "Bearer " + alice))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error_code", is("INVALID_GOAL_STATE")));
        mockMvc.perform(post("/goals"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error_code", is("UNAUTHORIZED")));
    }

    private long createGoal(String token, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/goals").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateGoalRequest(title, "Description", null))))
                .andExpect(status().isCreated()).andReturn();
        return json(result).get("id").asLong();
    }
    private String registerAndLogin(String email) throws Exception {
        String request = "{\"email\":\"" + email + "\",\"password\":\"securePassword123\"}";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isCreated());
        return json(mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andReturn()).get("token").asText();
    }
    private Long ownerId(String token) throws Exception {
        return json(mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn()).get("id").asLong();
    }
    private java.util.List<com.atlas.backend.event.Event> eventsFor(long goalId) {
        return eventRepository.findAllByEntityTypeAndEntityIdOrderByTimestamp("goal", goalId);
    }
    private JsonNode json(MvcResult result) throws Exception { return objectMapper.readTree(result.getResponse().getContentAsString()); }
    private void cleanDatabase() {
        eventRepository.deleteAll();
        goalRepository.deleteAll();
        taskRepository.deleteAll();
        userRepository.deleteAll();
    }
}
