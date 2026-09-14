package com.atlas.backend.category;

import com.atlas.backend.event.EventRepository;
import com.atlas.backend.goal.GoalRepository;
import com.atlas.backend.recurringintention.RecurringIntentionRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class CategoryIntegrationTest {
    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private RecurringIntentionRepository recurringIntentionRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private GoalRepository goalRepository;
    @Autowired private RoadmapRepository roadmapRepository;
    @Autowired private MilestoneRepository milestoneRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach void setUp() { cleanDatabase(); mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }
    @AfterEach void tearDown() { cleanDatabase(); }

    @Test
    void createsListsFetchesUpdatesAndDeletesUserCategories() throws Exception {
        String token = registerAndLogin("category-owner@example.com");
        long first = create(token, "Study", "protected", "#112233");
        create(token, "Health", "flexible", "blue");

        mockMvc.perform(get("/categories").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2))).andExpect(jsonPath("$[0].id", is((int) first)));
        mockMvc.perform(get("/categories/{id}", first).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name", is("Study")));
        mockMvc.perform(patch("/categories/{id}", first).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Deep study\",\"defaultFlexibilityTier\":\"fixed\",\"color\":\"#abc\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name", is("Deep study")))
                .andExpect(jsonPath("$.defaultFlexibilityTier", is("fixed"))).andExpect(jsonPath("$.color", is("#abc")));
        mockMvc.perform(delete("/categories/{id}", first).header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        mockMvc.perform(get("/categories/{id}", first).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error_code", is("CATEGORY_NOT_FOUND")));
    }

    @Test
    void routesAndRecurringLinkRejectForeignOrMissingCategories() throws Exception {
        String alice = registerAndLogin("category-alice@example.com");
        String bob = registerAndLogin("category-bob@example.com");
        long aliceCategory = create(alice, "Private", "optional", "red");

        mockMvc.perform(get("/categories/{id}", aliceCategory).header("Authorization", "Bearer " + bob))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error_code", is("CATEGORY_NOT_FOUND")));
        mockMvc.perform(patch("/categories/{id}", aliceCategory).header("Authorization", "Bearer " + bob)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"color\":\"green\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/categories/{id}", aliceCategory).header("Authorization", "Bearer " + bob)).andExpect(status().isNotFound());
        mockMvc.perform(post("/recurring-intentions").header("Authorization", "Bearer " + bob).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Foreign category\",\"targetCountPerWeek\":2,\"categoryId\":" + aliceCategory + ",\"flexibilityTier\":\"flexible\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error_code", is("CATEGORY_NOT_FOUND")));
        mockMvc.perform(post("/recurring-intentions").header("Authorization", "Bearer " + bob).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Missing category\",\"targetCountPerWeek\":2,\"categoryId\":999999,\"flexibilityTier\":\"flexible\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error_code", is("CATEGORY_NOT_FOUND")));
        assertEquals(0, recurringIntentionRepository.count());
    }

    @Test
    void referencedCategoryCannotBeDeletedAndForeignKeyAcceptsOwnedCategory() throws Exception {
        String token = registerAndLogin("category-reference@example.com");
        long category = create(token, "Training", "flexible", "#010101");
        mockMvc.perform(post("/recurring-intentions").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Run\",\"targetCountPerWeek\":3,\"categoryId\":" + category + ",\"flexibilityTier\":\"flexible\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.categoryId", is((int) category)));
        mockMvc.perform(delete("/categories/{id}", category).header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error_code", is("CATEGORY_IN_USE")));
        assertEquals(1, recurringIntentionRepository.count());
        assertEquals(1, categoryRepository.count());
    }

    private long create(String token, String name, String tier, String color) throws Exception {
        MvcResult result = mockMvc.perform(post("/categories").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateCategoryRequest(name, tier, color))))
                .andExpect(status().isCreated()).andReturn();
        return json(result).get("id").asLong();
    }
    private String registerAndLogin(String email) throws Exception {
        String request = "{\"email\":\"" + email + "\",\"password\":\"securePassword123\"}";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isCreated());
        return json(mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isOk()).andReturn()).get("token").asText();
    }
    private JsonNode json(MvcResult result) throws Exception { return objectMapper.readTree(result.getResponse().getContentAsString()); }
    private void cleanDatabase() {
        eventRepository.deleteAll(); recurringIntentionRepository.deleteAll(); categoryRepository.deleteAll(); milestoneRepository.deleteAll(); roadmapRepository.deleteAll(); goalRepository.deleteAll(); taskRepository.deleteAll(); userRepository.deleteAll();
    }
}
