package com.atlas.backend.dependency;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.atlas.backend.event.EventRepository;
import com.atlas.backend.security.JwtService;
import com.atlas.backend.user.User;
import com.atlas.backend.user.UserRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("test")
public class TaskOrganizationQaIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired EventRepository events;
    @Autowired DependencyService dependencyService;

    private MockMvc mvc;
    private final ObjectMapper mapper = new ObjectMapper();
    private String aliceToken, bobToken;
    private Long aliceId, bobId;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        User alice = users.save(User.of("alice-qa@example.com", "password123"));
        aliceId = alice.getId();
        aliceToken = jwt.generateToken(alice);

        User bob = users.save(User.of("bob-qa@example.com", "password123"));
        bobId = bob.getId();
        bobToken = jwt.generateToken(bob);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM events");
        jdbc.update("DELETE FROM commitment_dependency");
        jdbc.update("DELETE FROM commitments");
        jdbc.update("DELETE FROM milestones");
        jdbc.update("DELETE FROM roadmaps");
        jdbc.update("DELETE FROM goals");
        jdbc.update("DELETE FROM categories");
        jdbc.update("DELETE FROM users");
    }

    private long createGoal(String token, String title) throws Exception {
        String res = mvc.perform(post("/goals").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"" + title + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(res).path("id").asLong();
    }

    private long createCommitment(String token, String title, Long goalId, Long categoryId, String importance, String flexibility) throws Exception {
        StringBuilder sb = new StringBuilder("{");
        if (title != null) sb.append("\"title\":\"").append(title).append("\"");
        if (goalId != null) sb.append(sb.length() > 1 ? "," : "").append("\"goalId\":").append(goalId);
        if (categoryId != null) sb.append(sb.length() > 1 ? "," : "").append("\"categoryId\":").append(categoryId);
        if (importance != null) sb.append(sb.length() > 1 ? "," : "").append("\"importance\":\"").append(importance).append("\"");
        if (flexibility != null) sb.append(sb.length() > 1 ? "," : "").append("\"flexibilityTier\":\"").append(flexibility).append("\"");
        sb.append("}");
        String res = mvc.perform(post("/commitments").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(sb.toString()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(res).path("id").asLong();
    }

    private long createCategory(String token, String name, String color, String tier, String importance) throws Exception {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"name\":\"").append(name).append("\",");
        sb.append("\"color\":\"").append(color).append("\",");
        sb.append("\"defaultFlexibilityTier\":\"").append(tier).append("\"");
        if (importance != null) sb.append(",\"defaultImportance\":\"").append(importance).append("\"");
        sb.append("}");
        String res = mvc.perform(post("/categories").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(sb.toString()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(res).path("id").asLong();
    }

    @Test
    void testCategoryCreationDefaultsOverrideAndIsolation() throws Exception {
        // 1. Category creation
        long aliceCat = createCategory(aliceToken, "Work", "#112233", "protected", "high");

        // 2. Default category behavior on task creation
        long taskWithDefault = createCommitment(aliceToken, "Default Task", null, aliceCat, null, null);
        mvc.perform(get("/commitments/" + taskWithDefault).header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importance", is("high")))
                .andExpect(jsonPath("$.flexibilityTier", is("protected")))
                .andExpect(jsonPath("$.categoryId", is((int) aliceCat)));

        // 3. Per-task override on creation
        long taskWithOverride = createCommitment(aliceToken, "Overridden Task", null, aliceCat, "low", "flexible");
        mvc.perform(get("/commitments/" + taskWithOverride).header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importance", is("low")))
                .andExpect(jsonPath("$.flexibilityTier", is("flexible")))
                .andExpect(jsonPath("$.categoryId", is((int) aliceCat)));

        // 4. Verify override did not corrupt category defaults
        mvc.perform(get("/categories/" + aliceCat).header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultImportance", is("high")))
                .andExpect(jsonPath("$.defaultFlexibilityTier", is("protected")));

        // Another task created with this category still gets the original defaults
        long taskAnother = createCommitment(aliceToken, "Another Task", null, aliceCat, null, null);
        mvc.perform(get("/commitments/" + taskAnother).header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importance", is("high")))
                .andExpect(jsonPath("$.flexibilityTier", is("protected")));

        // 5. User ownership / isolation
        // Bob cannot see Alice's category
        mvc.perform(get("/categories/" + aliceCat).header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code", is("CATEGORY_NOT_FOUND")));

        // Bob cannot use Alice's category when creating a task
        mvc.perform(post("/commitments").header("Authorization", "Bearer " + bobToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Bob Task\",\"categoryId\":" + aliceCat + "}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code", is("COMMITMENT_NOT_FOUND")));

        // Bob cannot update or delete Alice's category
        mvc.perform(patch("/categories/" + aliceCat).header("Authorization", "Bearer " + bobToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Hacked\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/categories/" + aliceCat).header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void testPrerequisitesSearchCrossGoalAndUserScoping() throws Exception {
        long goalA = createGoal(aliceToken, "Goal Alpha");
        long goalB = createGoal(aliceToken, "Goal Beta");
        long bobsGoal = createGoal(bobToken, "Bob's Secret Goal");

        long taskA1 = createCommitment(aliceToken, "Prepare ingredients", goalA, null, "medium", "flexible");
        long taskB1 = createCommitment(aliceToken, "Cook dinner", goalB, null, "high", "fixed");
        long taskNoGoal = createCommitment(aliceToken, "Eat dinner", null, null, "low", "optional");
        long bobsTask = createCommitment(bobToken, "Bob's private cooking task", bobsGoal, null, "critical", "fixed");

        // Search across goals for "dinner" excluding taskNoGoal
        mvc.perform(get("/commitments").param("q", "dinner").param("excludeId", String.valueOf(taskNoGoal))
                .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commitments", hasSize(1)))
                .andExpect(jsonPath("$.commitments[0].id", is((int) taskB1)))
                .andExpect(jsonPath("$.commitments[0].title", is("Cook dinner")));

        // Search returns across goals: "ingredients" and "dinner"
        mvc.perform(get("/commitments").header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commitments", hasSize(3)));

        // User scoping: Alice never sees Bob's task, and Bob never sees Alice's tasks
        mvc.perform(get("/commitments").param("q", "cooking").header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commitments", hasSize(0)));

        mvc.perform(get("/commitments").param("q", "dinner").header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commitments", hasSize(0)));
    }

    @Test
    void testExplicitThreeNodeCycleRejectionAndIntegrityPreservation() throws Exception {
        // Explicitly test:
        // A -> B
        // B -> C
        // C -> A
        // and verify the system rejects the cycle without corrupting existing relationships.

        long a = createCommitment(aliceToken, "Task A", null, null, "high", "flexible");
        long b = createCommitment(aliceToken, "Task B", null, null, "high", "flexible");
        long c = createCommitment(aliceToken, "Task C", null, null, "high", "flexible");

        // 1. A blocks B (A -> B)
        mvc.perform(post("/commitments/" + b + "/dependencies")
                .header("Authorization", "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"blockingCommitmentId\":" + a + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.blockingCommitmentId", is((int) a)))
                .andExpect(jsonPath("$.blockedCommitmentId", is((int) b)));

        // 2. B blocks C (B -> C)
        mvc.perform(post("/commitments/" + c + "/dependencies")
                .header("Authorization", "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"blockingCommitmentId\":" + b + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.blockingCommitmentId", is((int) b)))
                .andExpect(jsonPath("$.blockedCommitmentId", is((int) c)));

        // 3. Attempt C blocks A (C -> A) -> MUST BE REJECTED (409 DEPENDENCY_CYCLE)
        mvc.perform(post("/commitments/" + a + "/dependencies")
                .header("Authorization", "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"blockingCommitmentId\":" + c + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code", is("DEPENDENCY_CYCLE")));

        // 4. Verify system did NOT corrupt existing relationships
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM commitment_dependency", Integer.class));

        // Inbound dependencies for B must still be [A]
        mvc.perform(get("/commitments/" + b + "/dependencies").header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].blockingCommitmentId", is((int) a)));

        // Inbound dependencies for C must still be [B]
        mvc.perform(get("/commitments/" + c + "/dependencies").header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].blockingCommitmentId", is((int) b)));

        // Inbound dependencies for A must be EMPTY
        mvc.perform(get("/commitments/" + a + "/dependencies").header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // Lookahead from A reaches B and C without error
        var lookaheadA = dependencyService.lookahead(aliceId, a);
        assertEquals(List.of(b, c), lookaheadA.downstreamIds());
        assertFalse(lookaheadA.cycleDetected());
    }

    @Test
    void testSelfDependencyDeeperCycleSharedDependencyCrossGoalAndRemoval() throws Exception {
        long goal1 = createGoal(aliceToken, "Goal 1");
        long goal2 = createGoal(aliceToken, "Goal 2");

        long a = createCommitment(aliceToken, "Node A", goal1, null, "medium", "flexible");
        long b = createCommitment(aliceToken, "Node B", goal2, null, "medium", "flexible");
        long c = createCommitment(aliceToken, "Node C", goal1, null, "medium", "flexible");
        long d = createCommitment(aliceToken, "Node D", goal2, null, "medium", "flexible");
        long e = createCommitment(aliceToken, "Node E", null, null, "medium", "flexible");

        // 1. Self dependency rejected
        mvc.perform(post("/commitments/" + a + "/dependencies").header("Authorization", "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"blockingCommitmentId\":" + a + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code", is("VALIDATION_ERROR")));

        // 2. Cross-goal dependency: A (in Goal 1) blocks B (in Goal 2)
        mvc.perform(post("/commitments/" + b + "/dependencies").header("Authorization", "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"blockingCommitmentId\":" + a + "}"))
                .andExpect(status().isCreated());

        // 3. Deeper cycle test: A -> B -> C -> D -> E -> A
        mvc.perform(post("/commitments/" + c + "/dependencies").header("Authorization", "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"blockingCommitmentId\":" + b + "}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/commitments/" + d + "/dependencies").header("Authorization", "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"blockingCommitmentId\":" + c + "}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/commitments/" + e + "/dependencies").header("Authorization", "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"blockingCommitmentId\":" + d + "}"))
                .andExpect(status().isCreated());

        // Attempt closing deeper cycle: E blocks A
        mvc.perform(post("/commitments/" + a + "/dependencies").header("Authorization", "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"blockingCommitmentId\":" + e + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code", is("DEPENDENCY_CYCLE")));

        // Attempt closing cycle across goals: D blocks A
        mvc.perform(post("/commitments/" + a + "/dependencies").header("Authorization", "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"blockingCommitmentId\":" + d + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error_code", is("DEPENDENCY_CYCLE")));

        // 4. Removing a dependency after creation
        // Remove B -> C link
        mvc.perform(delete("/commitments/" + c + "/dependencies/" + b).header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isNoContent());

        // Now C no longer depends on B, so E no longer reaches A!
        // Adding E -> A should now SUCCEED because the path A -> B -> C -> D -> E is broken at B -> C
        mvc.perform(post("/commitments/" + a + "/dependencies").header("Authorization", "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"blockingCommitmentId\":" + e + "}"))
                .andExpect(status().isCreated());

        // 5. Shared dependency (diamond DAG)
        // Let's create root -> left, root -> right, left -> target, right -> target
        long root = createCommitment(aliceToken, "Root", null, null, "high", "fixed");
        long left = createCommitment(aliceToken, "Left branch", null, null, "medium", "flexible");
        long right = createCommitment(aliceToken, "Right branch", null, null, "medium", "flexible");
        long target = createCommitment(aliceToken, "Target", null, null, "low", "optional");

        mvc.perform(post("/commitments/" + left + "/dependencies").header("Authorization", "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"blockingCommitmentId\":" + root + "}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/commitments/" + right + "/dependencies").header("Authorization", "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"blockingCommitmentId\":" + root + "}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/commitments/" + target + "/dependencies").header("Authorization", "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"blockingCommitmentId\":" + left + "}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/commitments/" + target + "/dependencies").header("Authorization", "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"blockingCommitmentId\":" + right + "}"))
                .andExpect(status().isCreated());

        // Inbound for target has both left and right
        mvc.perform(get("/commitments/" + target + "/dependencies").header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        var lookahead = dependencyService.lookahead(aliceId, root);
        assertFalse(lookahead.cycleDetected(), "Diamond DAG must not be flagged as a cycle");
        assertTrue(lookahead.downstreamIds().containsAll(List.of(left, right, target)));
    }

    @Test
    void testDuplicatePreventionAndInvalidMissingHandling() throws Exception {
        long taskX = createCommitment(aliceToken, "Task X", null, null, "high", "fixed");
        long taskY = createCommitment(aliceToken, "Task Y", null, null, "high", "fixed");

        // Add X blocks Y
        mvc.perform(post("/commitments/" + taskY + "/dependencies").header("Authorization", "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"blockingCommitmentId\":" + taskX + "}"))
                .andExpect(status().isCreated());

        long countBefore = jdbc.queryForObject("SELECT COUNT(*) FROM commitment_dependency", Integer.class);
        long eventsBefore = events.count();

        // Duplicate add: must return 200 and NOT create a new row or event
        mvc.perform(post("/commitments/" + taskY + "/dependencies").header("Authorization", "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"blockingCommitmentId\":" + taskX + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockingCommitmentId", is((int) taskX)))
                .andExpect(jsonPath("$.blockedCommitmentId", is((int) taskY)));

        assertEquals((int) countBefore, (int) jdbc.queryForObject("SELECT COUNT(*) FROM commitment_dependency", Integer.class));
        assertEquals(eventsBefore, events.count());

        // Missing blocker
        mvc.perform(post("/commitments/" + taskY + "/dependencies").header("Authorization", "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"blockingCommitmentId\":999999}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code", is("COMMITMENT_NOT_FOUND")));

        // Missing blocked
        mvc.perform(post("/commitments/999999/dependencies").header("Authorization", "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"blockingCommitmentId\":" + taskX + "}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code", is("COMMITMENT_NOT_FOUND")));

        // Foreign blocker (Bob owns Task Z, Alice tries to add Task Z as blocker)
        long bobsTask = createCommitment(bobToken, "Bob Task", null, null, "high", "fixed");
        mvc.perform(post("/commitments/" + taskY + "/dependencies").header("Authorization", "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"blockingCommitmentId\":" + bobsTask + "}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code", is("COMMITMENT_NOT_FOUND")));

        // Foreign blocked (Alice tries to add blocker to Bob's task)
        mvc.perform(post("/commitments/" + bobsTask + "/dependencies").header("Authorization", "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"blockingCommitmentId\":" + taskX + "}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error_code", is("COMMITMENT_NOT_FOUND")));

        // Delete non-existent dependency
        mvc.perform(delete("/commitments/" + taskY + "/dependencies/999999").header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isNotFound());
    }
}
