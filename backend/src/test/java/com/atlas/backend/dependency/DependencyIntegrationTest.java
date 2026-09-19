package com.atlas.backend.dependency;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import com.atlas.backend.event.EventRepository;
import com.atlas.backend.security.JwtService;
import com.atlas.backend.user.User;
import com.atlas.backend.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
    "spring.datasource.url=${dom007.api.url:jdbc:h2:mem:dom007_api;MODE=MySQL;DB_CLOSE_DELAY=-1}",
    "spring.datasource.username=${dom007.mysql.user:sa}",
    "spring.datasource.password=${dom007.mysql.password:}",
    "spring.datasource.driver-class-name=${dom007.mysql.driver:org.h2.Driver}"})
@ActiveProfiles("test")
class DependencyIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired EventRepository events;
    @Autowired DependencyService service;
    MockMvc mvc;
    final ObjectMapper mapper = new ObjectMapper();
    String token, foreign;
    Long owner, other;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        var a = users.save(User.of("dom007-owner@example.com", "test"));
        owner = a.getId();
        token = jwt.generateToken(a);
        var b = users.save(User.of("dom007-other@example.com", "test"));
        other = b.getId();
        foreign = jwt.generateToken(b);
    }

    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM events");
        jdbc.update("DELETE FROM commitment_dependency");
        jdbc.update("DELETE FROM commitments");
        jdbc.update("DELETE FROM users");
    }

    ResultActions postJson(String path, String body) throws Exception {
        return mvc.perform(post(path).header("Authorization", "Bearer " + token).contentType("application/json").content(body));
    }

    long commitment() throws Exception {
        return mapper.readTree(postJson("/commitments", "{\"importance\":\"high\",\"flexibilityTier\":\"flexible\",\"title\":\"T\",\"completionCriterion\":\"Done\"}")
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("id").asLong();
    }

    @Test void createListDuplicateAndDelete() throws Exception {
        long blocked = commitment();
        long blocking = commitment();
        postJson("/commitments/" + blocked + "/dependencies", "{\"blockingCommitmentId\":" + blocking + "}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.blockingCommitmentId", is((int) blocking)))
            .andExpect(jsonPath("$.blockedCommitmentId", is((int) blocked)));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM commitment_dependency", Integer.class));
        assertEquals("task.dependency_added", jdbc.queryForObject("SELECT type FROM events WHERE type LIKE 'task.dependency%' ORDER BY id DESC LIMIT 1", String.class));
        long eventsAfterAdd = events.count();
        postJson("/commitments/" + blocked + "/dependencies", "{\"blockingCommitmentId\":" + blocking + "}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.blockingCommitmentId", is((int) blocking)));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM commitment_dependency", Integer.class));
        assertEquals(eventsAfterAdd, events.count());
        mvc.perform(get("/commitments/" + blocked + "/dependencies").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].blockingCommitmentId", is((int) blocking)));
        mvc.perform(get("/commitments/" + blocking + "/dependencies").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(delete("/commitments/" + blocked + "/dependencies/" + blocking).header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM commitment_dependency", Integer.class));
        assertEquals("task.dependency_removed", jdbc.queryForObject("SELECT type FROM events ORDER BY id DESC LIMIT 1", String.class));
        mvc.perform(delete("/commitments/" + blocked + "/dependencies/" + blocking).header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound());
    }

    @Test void inboundListSortedByBlockerId() throws Exception {
        long blocked = commitment();
        long first = commitment();
        long second = commitment();
        long low = Math.min(first, second);
        long high = Math.max(first, second);
        postJson("/commitments/" + blocked + "/dependencies", "{\"blockingCommitmentId\":" + high + "}").andExpect(status().isCreated());
        postJson("/commitments/" + blocked + "/dependencies", "{\"blockingCommitmentId\":" + low + "}").andExpect(status().isCreated());
        mvc.perform(get("/commitments/" + blocked + "/dependencies").header("Authorization", "Bearer " + token))
            .andExpect(jsonPath("$[0].blockingCommitmentId", is((int) low)))
            .andExpect(jsonPath("$[1].blockingCommitmentId", is((int) high)));
    }

    @Test void selfTwoNodeAndLongCyclesRejected() throws Exception {
        long a = commitment();
        long b = commitment();
        long c = commitment();
        long d = commitment();
        postJson("/commitments/" + a + "/dependencies", "{\"blockingCommitmentId\":" + a + "}")
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error_code", is("VALIDATION_ERROR")));
        postJson("/commitments/" + b + "/dependencies", "{\"blockingCommitmentId\":" + a + "}").andExpect(status().isCreated());
        postJson("/commitments/" + a + "/dependencies", "{\"blockingCommitmentId\":" + b + "}")
            .andExpect(status().isConflict()).andExpect(jsonPath("$.error_code", is("DEPENDENCY_CYCLE")));
        postJson("/commitments/" + c + "/dependencies", "{\"blockingCommitmentId\":" + b + "}").andExpect(status().isCreated());
        postJson("/commitments/" + d + "/dependencies", "{\"blockingCommitmentId\":" + c + "}").andExpect(status().isCreated());
        postJson("/commitments/" + a + "/dependencies", "{\"blockingCommitmentId\":" + d + "}")
            .andExpect(status().isConflict()).andExpect(jsonPath("$.error_code", is("DEPENDENCY_CYCLE")));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM commitment_dependency", Integer.class));
    }

    @Test void ownershipAuthAndMalformedIds() throws Exception {
        long blocked = commitment();
        long blocking = commitment();
        jdbc.update("INSERT INTO commitments(user_id,importance,flexibility_tier,work_state) VALUES(?,'high','flexible','draft')", other);
        long foreignId = jdbc.queryForObject("SELECT MAX(id) FROM commitments", Long.class);
        mvc.perform(post("/commitments/" + blocked + "/dependencies").contentType("application/json").content("{\"blockingCommitmentId\":" + blocking + "}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/commitments/" + blocked + "/dependencies").header("Authorization", "Bearer " + foreign).contentType("application/json").content("{\"blockingCommitmentId\":" + blocking + "}"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.error_code", is("COMMITMENT_NOT_FOUND")));
        postJson("/commitments/" + blocked + "/dependencies", "{\"blockingCommitmentId\":" + foreignId + "}")
            .andExpect(status().isNotFound());
        postJson("/commitments/" + blocked + "/dependencies", "{\"blockingCommitmentId\":999999}")
            .andExpect(status().isNotFound());
        for (String body : new String[] {"{\"blockingCommitmentId\":0}", "{\"blockingCommitmentId\":-1}", "{\"blockingCommitmentId\":\"1\"}", "{\"blockingCommitmentId\":1.5}", "{}", "{\"blockingCommitmentId\":1,\"extra\":true}"}) {
            postJson("/commitments/" + blocked + "/dependencies", body).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/commitments/" + blocked + "/dependencies")).andExpect(status().isUnauthorized());
    }

    @Test void commitmentResponseDoesNotIncludeTransitiveDependencies() throws Exception {
        long blocked = commitment();
        long blocking = commitment();
        postJson("/commitments/" + blocked + "/dependencies", "{\"blockingCommitmentId\":" + blocking + "}").andExpect(status().isCreated());
        String body = mvc.perform(get("/commitments/" + blocked).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("blockingCommitmentId"));
        assertFalse(body.contains("dependencies"));
    }

    @Test void lookaheadIsDeterministicAndReportsTruncationNotAbsence() throws Exception {
        long a = commitment();
        long b = commitment();
        long c = commitment();
        long d = commitment();
        service.add(owner, b, a);
        service.add(owner, c, b);
        service.add(owner, d, c);
        var full = service.lookahead(owner, a);
        assertEquals(List.of(b, c, d), full.downstreamIds());
        assertFalse(full.truncated());
        var bounded = DependencyLookahead.downstream(a, jdbc.query(
            "SELECT blocking_commitment_id, blocked_commitment_id FROM commitment_dependency",
            (rs, i) -> CommitmentDependency.of(rs.getLong(1), rs.getLong(2))), 1, 64);
        assertEquals(List.of(b), bounded.downstreamIds());
        assertTrue(bounded.truncated());
        assertFalse(bounded.downstreamIds().isEmpty());
    }
}
