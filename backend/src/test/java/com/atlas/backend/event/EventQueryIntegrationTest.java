package com.atlas.backend.event;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.atlas.backend.goal.*;
import com.atlas.backend.security.JwtService;
import com.atlas.backend.user.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:evt004;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class EventQueryIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;
    @Autowired GoalService goals;
    @Autowired EventRepository events;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    MockMvc mvc;
    String token, otherToken;
    Long owner, goal;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        var user = users.save(User.of("evt004@example.com", "test"));
        owner = user.getId(); token = jwt.generateToken(user);
        otherToken = jwt.generateToken(users.save(User.of("evt004-other@example.com", "test")));
        goal = goals.create(owner, new CreateGoalRequest("Private goal", null, null)).id();
        goals.markAtRisk(owner, goal);
    }
    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM events"); jdbc.update("DELETE FROM goals"); jdbc.update("DELETE FROM users");
    }
    String path() { return "/events?entity_type=goal&entity_id=" + goal; }

    @Test void rendersActualAtlasReasonWithoutChangingPersistedHistory() throws Exception {
        var before = jdbc.queryForList("SELECT * FROM events ORDER BY id");
        String reason = jdbc.queryForObject("SELECT reason FROM events WHERE actor='atlas'", String.class);
        mvc.perform(get(path() + "&actor=atlas").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.events.length()").value(1))
            .andExpect(jsonPath("$.events[0].actor").value("atlas"))
            .andExpect(jsonPath("$.events[0].reason").value(reason))
            .andExpect(jsonPath("$.events[0].description").value("Atlas marked this goal as at risk. " + reason));
        assertEquals(before, jdbc.queryForList("SELECT * FROM events ORDER BY id"));
    }
    @Test void rawQueryRetainsUserEventsAndPaginationSurvivesNewAppendsAndEqualTimestamps() throws Exception {
        jdbc.update("UPDATE events SET timestamp = '2026-09-21 00:00:00'");
        var first = mvc.perform(get(path() + "&limit=1").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.events[0].type").value("goal.at_risk"))
            .andReturn().getResponse().getContentAsString();
        long cursor = new ObjectMapper().readTree(first).path("nextCursor").asLong();
        goals.resolveRisk(owner, goal);
        mvc.perform(get(path() + "&limit=1&cursor=" + cursor).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.events[0].type").value("goal.created"))
            .andExpect(jsonPath("$.events.length()").value(1)).andExpect(jsonPath("$.nextCursor").isEmpty());
    }
    @Test void ownershipMissingAndAuthenticationAreEnforced() throws Exception {
        var foreign = mvc.perform(get(path()).header("Authorization", "Bearer " + otherToken))
            .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        var missing = mvc.perform(get("/events?entity_type=goal&entity_id=9223372036854775806")
            .header("Authorization", "Bearer " + token)).andExpect(status().isNotFound())
            .andReturn().getResponse().getContentAsString();
        assertEquals(foreign, missing);
        mvc.perform(get(path())).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error_code").value("UNAUTHORIZED"));
    }
    @Test void rejectsInvalidQueries() throws Exception {
        for (String query : new String[]{"&limit=0", "&limit=101", "&cursor=-1", "&cursor=x", "&actor=admin"}) {
            mvc.perform(get(path() + query).header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error_code").value("VALIDATION_ERROR"));
        }
        mvc.perform(get("/events?entity_type=unknown&entity_id=1").header("Authorization", "Bearer " + token))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/events").header("Authorization", "Bearer " + token)).andExpect(status().isBadRequest());
    }
    @Test void unknownAtlasTypeAndMissingReasonDoNotInventAnExplanation() throws Exception {
        new TransactionTemplate(transactions).executeWithoutResult(tx ->
            events.appendAndFlush(Event.forEntity("goal", goal, "goal.future_type", "atlas")));
        mvc.perform(get(path() + "&actor=atlas&limit=1").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.events[0].reason").isEmpty())
            .andExpect(jsonPath("$.events[0].description").value("Atlas recorded a change to this goal."));
    }
    @Test void actorFilteringBeforePaginationCanReturnEmptyPage() throws Exception {
        long created = jdbc.queryForObject("SELECT MIN(id) FROM events", Long.class);
        mvc.perform(get(path() + "&actor=atlas&cursor=" + created).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.events.length()").value(0))
            .andExpect(jsonPath("$.nextCursor").isEmpty());
    }
}
