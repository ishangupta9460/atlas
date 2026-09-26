package com.atlas.backend.scheduling;

import com.atlas.backend.security.JwtService;
import com.atlas.backend.user.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:scheduling_foundation;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
@ActiveProfiles("test")
class SchedulingFoundationIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate db;
    @Autowired SchedulingFoundationService foundation;
    @Autowired WorkingHoursService workingHours;
    MockMvc mvc;
    Long owner, other;
    String token, foreign;
    static final String CAPACITY = "{\"workableFraction\":0.85,\"bufferMinutes\":15,\"continuousWorkMinutes\":50,\"breakMinutes\":10}";
    static final String HOURS = """
            {"timezone":"UTC","windows":[
              {"dayOfWeek":5,"startTime":"09:00","endTime":"17:00","kind":"working"},
              {"dayOfWeek":5,"startTime":"15:00","endTime":"15:30","kind":"protected"}]}
            """;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        var user = users.save(User.of(UUID.randomUUID()+"@test.example", "unused"));
        owner = user.getId(); token = jwt.generateToken(user);
        var second = users.save(User.of(UUID.randomUUID()+"@test.example", "unused"));
        other = second.getId(); foreign = jwt.generateToken(second);
    }

    @AfterEach void cleanConstraint() { db.execute("ALTER TABLE events DROP CONSTRAINT IF EXISTS reject_scheduling_event"); }

    ResultActions capacityPut(String auth, String body) throws Exception {
        return mvc.perform(put("/users/me/capacity").header("Authorization", "Bearer "+auth)
                .contentType("application/json").content(body));
    }

    @Test void capacityIsAuthenticatedOwnedValidatedAndIdempotent() throws Exception {
        mvc.perform(get("/users/me/capacity")).andExpect(status().isUnauthorized());
        mvc.perform(put("/users/me/capacity").contentType("application/json").content(CAPACITY)).andExpect(status().isUnauthorized());
        mvc.perform(get("/users/me/capacity").header("Authorization", "Bearer "+token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.workableFraction").value(0.7));
        capacityPut(token, CAPACITY).andExpect(status().isOk()).andExpect(jsonPath("$.workableFraction").value(0.85));
        capacityPut(token, CAPACITY).andExpect(status().isOk());
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM events WHERE entity_type='scheduling_config' AND entity_id=?", Integer.class, owner));
        mvc.perform(get("/users/me/capacity").header("Authorization", "Bearer "+foreign))
                .andExpect(status().isOk()).andExpect(jsonPath("$.workableFraction").value(0.7));
        // Even injected identity fields cannot select another tenant.
        capacityPut(foreign, CAPACITY.replace("0.85", "0.55").replace("{", "{\"userId\":"+owner+","))
                .andExpect(status().isOk());
        assertEquals(0.85, db.queryForObject("SELECT workable_fraction FROM scheduling_config WHERE user_id=?", Double.class, owner));
        capacityPut(token, CAPACITY.replace("0.85", "1.01")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error_code").value("VALIDATION_ERROR"));
        capacityPut(token, "{}").andExpect(status().isBadRequest());
        capacityPut(token, "{").andExpect(status().isBadRequest());
    }

    @Test void configurationAndAuditRollbackTogether() throws Exception {
        db.execute("ALTER TABLE events ADD CONSTRAINT reject_scheduling_event CHECK (type <> 'capacity.updated' OR entity_id <> " + owner + ")");
        capacityPut(token, CAPACITY).andExpect(status().isInternalServerError());
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM scheduling_config WHERE user_id=?", Integer.class, owner));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM events WHERE entity_type='scheduling_config' AND entity_id=?", Integer.class, owner));
    }

    ResultActions hoursPut(String auth, String body) throws Exception {
        return mvc.perform(put("/users/me/working-hours").header("Authorization", "Bearer "+auth)
                .contentType("application/json").content(body));
    }
    ResultActions query(String auth) throws Exception {
        return mvc.perform(get("/schedule/candidates").header("Authorization", "Bearer "+auth)
                .param("startTime", "2026-09-25T09:00:00Z").param("endTime", "2026-09-25T17:00:00Z").param("workMinutes", "30"));
    }
    void fixed(Long user, String start, String end) {
        db.update("INSERT INTO fixed_commitments(user_id,title,start_time,end_time,source) VALUES(?,'Reservation',?,?,'manual')", user,
                java.time.LocalDateTime.parse(start), java.time.LocalDateTime.parse(end));
    }
    void block(String start, String end, String state) {
        db.update("INSERT INTO recurring_intentions(user_id,title,target_count_per_week,current_week_remaining_count,flexibility_tier) VALUES(?,'Work',1,1,'flexible')", owner);
        Long intention = db.queryForObject("SELECT MAX(id) FROM recurring_intentions WHERE user_id=?", Long.class, owner);
        db.update("INSERT INTO scheduled_blocks(user_id,recurring_intention_id,start_time,end_time,state,placement_reason) VALUES(?,?,?,?,?,'Existing')",
                owner, intention, java.time.LocalDateTime.parse(start), java.time.LocalDateTime.parse(end), state);
    }

    @Test void workingHoursRequireAuthAreOwnedValidatedAndReplaceAtomically() throws Exception {
        mvc.perform(get("/users/me/working-hours")).andExpect(status().isUnauthorized());
        mvc.perform(put("/users/me/working-hours").contentType("application/json").content(HOURS)).andExpect(status().isUnauthorized());
        query(token).andExpect(status().isOk()).andExpect(jsonPath("$.configured").value(false)).andExpect(jsonPath("$.candidates").isEmpty());
        hoursPut(token, HOURS).andExpect(status().isOk()).andExpect(jsonPath("$.configured").value(true));
        hoursPut(token, HOURS).andExpect(status().isOk());
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM events WHERE entity_type='scheduling_config' AND entity_id=?", Integer.class, owner));
        mvc.perform(get("/users/me/working-hours").header("Authorization", "Bearer "+foreign))
                .andExpect(status().isOk()).andExpect(jsonPath("$.configured").value(false));
        hoursPut(foreign, HOURS.replace("UTC", "Asia/Kolkata").replace("{\"timezone\"", "{\"userId\":"+owner+",\"timezone\""))
                .andExpect(status().isOk());
        assertEquals("UTC", workingHours.get(owner).timezone());
        hoursPut(token, HOURS.replace("UTC", "bad/zone")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error_code").value("VALIDATION_ERROR"));
        hoursPut(token, HOURS.replace("protected", "working")).andExpect(status().isBadRequest());
        hoursPut(token, HOURS.replace("09:00", "17:00")).andExpect(status().isBadRequest());
        assertEquals(2, workingHours.get(owner).windows().size());
        hoursPut(token, "{\"timezone\":\"UTC\",\"windows\":[]}").andExpect(status().isOk());
        query(token).andExpect(status().isOk()).andExpect(jsonPath("$.candidates").isEmpty())
                .andExpect(jsonPath("$.days[0].capacity.remainingWorkSeconds").value(0));
    }

    @Test void realQueryExcludesFixedProtectedAndV11BlocksWithBuffersAndDeterministicCapacity() throws Exception {
        hoursPut(token, HOURS).andExpect(status().isOk());
        fixed(owner, "2026-09-25T10:00:00", "2026-09-25T11:00:00");
        fixed(owner, "2026-09-25T10:15:00", "2026-09-25T10:45:00"); // Overlap is counted once.
        fixed(other, "2026-09-25T09:00:00", "2026-09-25T17:00:00");
        block("2026-09-25T12:00:00", "2026-09-25T13:00:00", "scheduled");
        block("2026-09-25T14:00:00", "2026-09-25T15:00:00", "superseded");
        Long events = db.queryForObject("SELECT COUNT(*) FROM events", Long.class);
        String first = query(token).andExpect(status().isOk()).andExpect(jsonPath("$.candidates.length()").value(4))
                .andExpect(jsonPath("$.candidates[0].earliestStart").value("2026-09-25T09:00:00Z"))
                .andExpect(jsonPath("$.candidates[0].latestStart").value("2026-09-25T09:20:00Z"))
                .andExpect(jsonPath("$.candidates[1].earliestStart").value("2026-09-25T11:10:00Z"))
                .andExpect(jsonPath("$.days[0].capacity.rawFreeSeconds").value(390 * 60))
                .andExpect(jsonPath("$.days[0].capacity.workableSeconds").value(273 * 60))
                .andExpect(jsonPath("$.days[0].capacity.occupiedWorkSeconds").value(50 * 60))
                .andExpect(jsonPath("$.days[0].capacity.remainingWorkSeconds").value(223 * 60))
                .andReturn().getResponse().getContentAsString();
        for (int i = 0; i < 10; i++) assertEquals(first, query(token).andReturn().getResponse().getContentAsString());
        assertEquals(events, db.queryForObject("SELECT COUNT(*) FROM events", Long.class));
        assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM scheduled_blocks WHERE user_id=?", Integer.class, owner));
        // Active and completed reservations still occupy time; only superseded history is ignored.
        for (String state : new String[]{"active", "completed"}) {
            db.update("UPDATE scheduled_blocks SET state=? WHERE user_id=? AND state<>'superseded'", state, owner);
            assertEquals(first, query(token).andReturn().getResponse().getContentAsString());
        }
    }

    @Test void timezoneEditDoesNotMovePersistedBlocksAndNarrowQueryKeepsDailyBudget() throws Exception {
        hoursPut(token, HOURS).andExpect(status().isOk());
        block("2026-09-25T09:00:00", "2026-09-25T15:00:00", "scheduled");
        var before = db.queryForList("SELECT start_time,end_time FROM scheduled_blocks WHERE user_id=?", owner);
        var narrow = foundation.query(owner, java.time.Instant.parse("2026-09-25T16:00:00Z"), java.time.Instant.parse("2026-09-25T17:00:00Z"), 30);
        // Daily budget = 450 * .7 = 315 min; existing 6h block delivers 300 min.
        assertEquals(15 * 60, narrow.days().get(0).capacity().remainingWorkSeconds());
        hoursPut(token, HOURS.replace("UTC", "Asia/Kolkata")).andExpect(status().isOk());
        assertEquals(before, db.queryForList("SELECT start_time,end_time FROM scheduled_blocks WHERE user_id=?", owner));
        assertEquals("Asia/Kolkata", workingHours.get(owner).timezone());
    }

    @Test void queryRejectsUnauthenticatedMalformedAndUnboundedRequests() throws Exception {
        mvc.perform(get("/schedule/candidates")).andExpect(status().isUnauthorized());
        mvc.perform(get("/schedule/candidates").header("Authorization", "Bearer "+token)).andExpect(status().isBadRequest());
        for (String start : new String[]{"2026-09-25", "2026-09-25T09:00:00", "2020-01-01T00:00:00Z"}) {
            mvc.perform(get("/schedule/candidates").header("Authorization", "Bearer "+token).param("startTime", start)
                    .param("endTime", "2026-09-25T17:00:00Z").param("workMinutes", "30"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error_code").value("VALIDATION_ERROR"));
        }
        assertThrows(IllegalArgumentException.class, () -> foundation.query(owner, java.time.Instant.EPOCH, java.time.Instant.EPOCH.plusSeconds(3600), 0));
        capacityPut(token, CAPACITY.replace("\"bufferMinutes\":15,", "")).andExpect(status().isBadRequest());
    }

    @Test void workingHoursUpdateAndAuditRollbackTogether() throws Exception {
        hoursPut(token, HOURS).andExpect(status().isOk());
        db.execute("ALTER TABLE events ADD CONSTRAINT reject_scheduling_event CHECK (type <> 'working_hours.updated' OR entity_id <> " + owner + " OR id <= "
                + db.queryForObject("SELECT MAX(id) FROM events", Long.class) + ")");
        hoursPut(token, HOURS.replace("UTC", "Asia/Kolkata")).andExpect(status().isInternalServerError());
        assertEquals("UTC", workingHours.get(owner).timezone());
        assertEquals(2, workingHours.get(owner).windows().size());
    }

    @Test void persistedDstConfigurationProducesCorrectSlotsAndCapacity() throws Exception {
        hoursPut(token, """
                {"timezone":"America/New_York","windows":[
                  {"dayOfWeek":7,"startTime":"00:00","endTime":"04:00","kind":"working"},
                  {"dayOfWeek":7,"startTime":"01:15","endTime":"01:30","kind":"sleep"}]}
                """).andExpect(status().isOk());
        var fall = foundation.query(owner, java.time.Instant.parse("2026-11-01T04:00:00Z"), java.time.Instant.parse("2026-11-01T09:00:00Z"), 10);
        assertEquals(270 * 60, fall.days().get(0).capacity().rawFreeSeconds());
        assertEquals(3, fall.candidates().size());
        var spring = foundation.query(owner, java.time.Instant.parse("2026-03-08T05:00:00Z"), java.time.Instant.parse("2026-03-08T08:00:00Z"), 10);
        assertEquals(165 * 60, spring.days().get(0).capacity().rawFreeSeconds());
        // A different authenticated owner with the same configuration sees its own reservations only.
        hoursPut(foreign, HOURS).andExpect(status().isOk());
        fixed(owner, "2026-09-25T09:00:00", "2026-09-25T17:00:00");
        query(foreign).andExpect(status().isOk()).andExpect(jsonPath("$.candidates.length()").value(2));
    }
}
