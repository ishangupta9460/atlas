package com.atlas.backend.roadmap;

import com.atlas.backend.event.EventRepository;
import com.atlas.backend.goal.GoalRepository;
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
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class RoadmapIntegrationTest {
    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private GoalRepository goalRepository;
    @Autowired private RoadmapRepository roadmapRepository;
    @Autowired private MilestoneRepository milestoneRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach void setUp() {
        cleanDatabase();
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @AfterEach void tearDown() { cleanDatabase(); }

    @Test
    void roadmapAndMilestoneCrudUseStructuralFieldsAndOrderedRoadmapFetch() throws Exception {
        String token = registerAndLogin("roadmap-owner@example.com");
        long goalId = createGoal(token, "Learn Spring");
        long roadmapId = createRoadmap(token, goalId, "{}");

        long second = createMilestone(token, roadmapId, "Second", 2);
        long first = createMilestone(token, roadmapId, "First", 1);
        mockMvc.perform(get("/roadmaps/{id}", roadmapId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goalId", is((int) goalId)))
                .andExpect(jsonPath("$.source", is("user_interview")))
                .andExpect(jsonPath("$.milestones[0].id", is((int) first)))
                .andExpect(jsonPath("$.milestones[1].id", is((int) second)));
        mockMvc.perform(patch("/milestones/{id}", second).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Updated\",\"order\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title", is("Updated")))
                .andExpect(jsonPath("$.order", is(0)));
        mockMvc.perform(get("/milestones/{id}", second).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title", is("Updated")));
        mockMvc.perform(patch("/roadmaps/{id}", roadmapId).header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error_code", is("ROADMAP_IMMUTABLE")));
    }

    @Test
    void importedSourceIsAcceptedButInvalidParentsAndPayloadsAreRejected() throws Exception {
        String token = registerAndLogin("roadmap-validation@example.com");
        long goalId = createGoal(token, "Valid parent");
        long roadmapId = createRoadmap(token, goalId, "{\"source\":\"imported\"}");
        mockMvc.perform(get("/roadmaps/{id}", roadmapId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.source", is("imported")));
        mockMvc.perform(post("/goals/{id}/roadmaps", 999999L).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error_code", is("GOAL_NOT_FOUND")));
        mockMvc.perform(post("/roadmaps/{id}/milestones", 999999L).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"No parent\",\"order\":1}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error_code", is("ROADMAP_NOT_FOUND")));
        mockMvc.perform(post("/roadmaps/{id}/milestones", roadmapId).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\" \",\"order\":1}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error_code", is("VALIDATION_ERROR")));
    }

    @Test
    void secondRoadmapForTheSameGoalReturnsConflict() throws Exception {
        String token = registerAndLogin("roadmap-duplicate@example.com");
        long goalId = createGoal(token, "One roadmap only");
        createRoadmap(token, goalId, "{}");

        mockMvc.perform(post("/goals/{id}/roadmaps", goalId).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code", is("ROADMAP_ALREADY_EXISTS")));
    }

    @Test
    void everyRouteFollowsFullGoalRoadmapMilestoneOwnershipChain() throws Exception {
        String alice = registerAndLogin("roadmap-alice@example.com");
        String bob = registerAndLogin("roadmap-bob@example.com");
        long aliceGoal = createGoal(alice, "Alice goal");
        long aliceRoadmap = createRoadmap(alice, aliceGoal, "{}");
        long aliceMilestone = createMilestone(alice, aliceRoadmap, "Private", 1);

        mockMvc.perform(post("/goals/{id}/roadmaps", aliceGoal).header("Authorization", "Bearer " + bob)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error_code", is("GOAL_NOT_FOUND")));
        mockMvc.perform(get("/roadmaps/{id}", aliceRoadmap).header("Authorization", "Bearer " + bob))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/roadmaps/{id}", aliceRoadmap).header("Authorization", "Bearer " + bob))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/roadmaps/{id}/milestones", aliceRoadmap).header("Authorization", "Bearer " + bob)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Intrusion\",\"order\":1}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/milestones/{id}", aliceMilestone).header("Authorization", "Bearer " + bob))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/milestones/{id}", aliceMilestone).header("Authorization", "Bearer " + bob)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Intrusion\"}"))
                .andExpect(status().isNotFound());
    }

    private long createGoal(String token, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/goals").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"" + title + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        return json(result).get("id").asLong();
    }
    private long createRoadmap(String token, long goalId, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/goals/{id}/roadmaps", goalId).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        return json(result).get("id").asLong();
    }
    private long createMilestone(String token, long roadmapId, String title, int order) throws Exception {
        MvcResult result = mockMvc.perform(post("/roadmaps/{id}/milestones", roadmapId).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateMilestoneRequest(title, order))))
                .andExpect(status().isCreated()).andReturn();
        return json(result).get("id").asLong();
    }
    private String registerAndLogin(String email) throws Exception {
        String request = "{\"email\":\"" + email + "\",\"password\":\"securePassword123\"}";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isCreated());
        return json(mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andReturn()).get("token").asText();
    }
    private JsonNode json(MvcResult result) throws Exception { return objectMapper.readTree(result.getResponse().getContentAsString()); }
    private void cleanDatabase() {
        eventRepository.deleteAll();
        milestoneRepository.deleteAll();
        roadmapRepository.deleteAll();
        goalRepository.deleteAll();
        taskRepository.deleteAll();
        userRepository.deleteAll();
    }
}
