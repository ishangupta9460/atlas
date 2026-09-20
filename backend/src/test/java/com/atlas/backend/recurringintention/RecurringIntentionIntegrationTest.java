package com.atlas.backend.recurringintention;

import com.atlas.backend.event.Event;
import com.atlas.backend.event.EventRepository;
import com.atlas.backend.goal.GoalRepository;
import com.atlas.backend.roadmap.MilestoneRepository;
import com.atlas.backend.roadmap.RoadmapRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class RecurringIntentionIntegrationTest {
    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private RecurringIntentionRepository recurringIntentionRepository;
    @Autowired private GoalRepository goalRepository;
    @Autowired private RoadmapRepository roadmapRepository;
    @Autowired private MilestoneRepository milestoneRepository;
    @Autowired private RecurringIntentionService recurringIntentionService;
    @Autowired private JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach void setUp() { cleanDatabase(); mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }
    @AfterEach void tearDown() { cleanDatabase(); }

    @Test
    void weeklyCycleResetsInFullCompletesAtZeroFloorAndMissDoesNotChangeRemaining() throws Exception {
        String token = registerAndLogin("recurring-cycle@example.com");
        long id = create(token, "Read", 2);
        Long userId = ownerId(token);

        mockMvc.perform(post("/recurring-intentions/{id}/instances/complete", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currentWeekRemainingCount", is(1)));
        jdbcTemplate.update("UPDATE recurring_intentions SET current_week_remaining_count = ? WHERE id = ?", 10, id);
        recurringIntentionService.resetForNewWeek(userId, id);
        assertEquals(2, recurringIntentionRepository.findById(id).orElseThrow().getCurrentWeekRemainingCount());

        mockMvc.perform(post("/recurring-intentions/{id}/instances/complete", id).header("Authorization", "Bearer " + token)).andExpect(jsonPath("$.currentWeekRemainingCount", is(1)));
        mockMvc.perform(post("/recurring-intentions/{id}/instances/complete", id).header("Authorization", "Bearer " + token)).andExpect(jsonPath("$.currentWeekRemainingCount", is(0)));
        mockMvc.perform(post("/recurring-intentions/{id}/instances/complete", id).header("Authorization", "Bearer " + token)).andExpect(jsonPath("$.currentWeekRemainingCount", is(0)));
        recurringIntentionService.resetForNewWeek(userId, id);
        assertEquals(2, recurringIntentionRepository.findById(id).orElseThrow().getCurrentWeekRemainingCount());

        mockMvc.perform(post("/recurring-intentions/{id}/instances/complete", id).header("Authorization", "Bearer " + token)).andExpect(jsonPath("$.currentWeekRemainingCount", is(1)));
        mockMvc.perform(post("/recurring-intentions/{id}/instances/miss", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currentWeekRemainingCount", is(1)));
    }

    @Test
    void targetUpdateAppliesAtNextResetAndCycleEventsHaveSpecifiedActorsAndReasons() throws Exception {
        String token = registerAndLogin("recurring-events@example.com");
        long id = create(token, "Exercise", 3);
        Long userId = ownerId(token);
        mockMvc.perform(post("/recurring-intentions/{id}/instances/complete", id).header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mockMvc.perform(patch("/recurring-intentions/{id}/target", id).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"targetCountPerWeek\":5}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.targetCountPerWeek", is(5))).andExpect(jsonPath("$.currentWeekRemainingCount", is(2)));
        mockMvc.perform(post("/recurring-intentions/{id}/instances/miss", id).header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        recurringIntentionService.resetForNewWeek(userId, id);
        assertEquals(5, recurringIntentionRepository.findById(id).orElseThrow().getCurrentWeekRemainingCount());

        List<Event> events = eventRepository.findAllByEntityTypeAndEntityIdOrderByTimestamp("recurring_intention", id);
        Event complete = events.stream().filter(event -> event.getType().equals("recurring_intention.instance_completed")).findFirst().orElseThrow();
        Event missed = events.stream().filter(event -> event.getType().equals("recurring_intention.instance_missed")).findFirst().orElseThrow();
        Event reset = events.stream().filter(event -> event.getType().equals("recurring_intention.reset")).findFirst().orElseThrow();
        Event created = events.stream().filter(event -> event.getType().equals("recurring_intention.created")).findFirst().orElseThrow();
        Event targetUpdated = events.stream().filter(event -> event.getType().equals("recurring_intention.target_updated")).findFirst().orElseThrow();
        assertEquals("user", created.getActor()); assertNull(created.getReason());
        assertEquals("user", targetUpdated.getActor()); assertNull(targetUpdated.getReason());
        assertEquals("user", complete.getActor()); assertNull(complete.getReason());
        assertEquals("user", missed.getActor()); assertNull(missed.getReason());
        assertEquals("atlas", reset.getActor()); assertEquals("A new week began; the recurring weekly target was reset in full without carrying prior-week backlog.", reset.getReason());
    }

    @Test
    void createFetchAndActionsRejectForeignOwnedIntentionWithStandard404() throws Exception {
        String alice = registerAndLogin("recurring-alice@example.com");
        String bob = registerAndLogin("recurring-bob@example.com");
        long id = create(alice, "Private habit", 2);
        mockMvc.perform(get("/recurring-intentions/{id}", id).header("Authorization", "Bearer " + bob)).andExpect(status().isNotFound()).andExpect(jsonPath("$.error_code", is("RECURRING_INTENTION_NOT_FOUND")));
        mockMvc.perform(patch("/recurring-intentions/{id}/target", id).header("Authorization", "Bearer " + bob).contentType(MediaType.APPLICATION_JSON).content("{\"targetCountPerWeek\":4}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error_code", is("RECURRING_INTENTION_NOT_FOUND")));
        mockMvc.perform(post("/recurring-intentions/{id}/instances/complete", id).header("Authorization", "Bearer " + bob)).andExpect(status().isNotFound());
        mockMvc.perform(post("/recurring-intentions/{id}/instances/miss", id).header("Authorization", "Bearer " + bob)).andExpect(status().isNotFound());
    }

    @Test
    void createRejectsGoalOwnedByAnotherUser() throws Exception {
        String alice = registerAndLogin("recurring-create-alice@example.com");
        String bob = registerAndLogin("recurring-create-bob@example.com");
        long bobGoalId = createGoal(bob, "Bob private goal");

        mockMvc.perform(post("/recurring-intentions").header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateRecurringIntentionRequest(bobGoalId, "Unauthorized link", 2, null, "flexible"))))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error_code", is("GOAL_NOT_FOUND")));
        assertEquals(0, recurringIntentionRepository.count());
    }

    private long create(String token, String title, int target) throws Exception {
        MvcResult result = mockMvc.perform(post("/recurring-intentions").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateRecurringIntentionRequest(null, title, target, null, "flexible"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.currentWeekRemainingCount", is(target))).andReturn();
        return json(result).get("id").asLong();
    }
    private long createGoal(String token, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/goals").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"description\":\"test\"}"))
                .andExpect(status().isCreated()).andReturn();
        return json(result).get("id").asLong();
    }
    private String registerAndLogin(String email) throws Exception {
        String request = "{\"email\":\"" + email + "\",\"password\":\"securePassword123\"}";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isCreated());
        return json(mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isOk()).andReturn()).get("token").asText();
    }
    private Long ownerId(String token) throws Exception { return json(mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn()).get("id").asLong(); }
    private JsonNode json(MvcResult result) throws Exception { return objectMapper.readTree(result.getResponse().getContentAsString()); }
    private void cleanDatabase() {
        eventRepository.deleteAllForTest(); recurringIntentionRepository.deleteAll(); milestoneRepository.deleteAll(); roadmapRepository.deleteAll(); goalRepository.deleteAll(); taskRepository.deleteAll(); userRepository.deleteAll();
    }
}
