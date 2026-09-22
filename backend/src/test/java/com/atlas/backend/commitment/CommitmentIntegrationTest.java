package com.atlas.backend.commitment;

import com.atlas.backend.user.*;
import com.atlas.backend.security.JwtService;
import com.atlas.backend.event.EventRepository;
import tools.jackson.databind.ObjectMapper;
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
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={
    "spring.datasource.url=${dom003.api.url:jdbc:h2:mem:dom003_api;MODE=MySQL;DB_CLOSE_DELAY=-1}",
    "spring.datasource.username=${dom003.mysql.user:sa}", "spring.datasource.password=${dom003.mysql.password:}",
    "spring.datasource.driver-class-name=${dom003.mysql.driver:org.h2.Driver}"})
@ActiveProfiles("test")
class CommitmentIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired EventRepository events;
    MockMvc mvc;
    final ObjectMapper mapper=new ObjectMapper();
    String token, foreign;
    Long owner, other;
    @BeforeEach void setup() {
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        var a=users.save(User.of("dom003-owner@example.com","test")); owner=a.getId(); token=jwt.generateToken(a);
        var b=users.save(User.of("dom003-other@example.com","test")); other=b.getId(); foreign=jwt.generateToken(b);
    }
    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM events"); jdbc.update("DELETE FROM commitments"); jdbc.update("DELETE FROM tasks");
        jdbc.update("DELETE FROM milestones"); jdbc.update("DELETE FROM roadmaps"); jdbc.update("DELETE FROM goals"); jdbc.update("DELETE FROM categories"); jdbc.update("DELETE FROM users");
    }
    ResultActions postJson(String path,String body) throws Exception { return mvc.perform(post(path).header("Authorization","Bearer "+token).contentType("application/json").content(body)); }
    ResultActions patchJson(String path,String body) throws Exception { return mvc.perform(patch(path).header("Authorization","Bearer "+token).contentType("application/json").content(body)); }
    long created(ResultActions call) throws Exception { return mapper.readTree(call.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("id").asLong(); }
    long commitment(String extra) throws Exception { return created(postJson("/commitments","{\"importance\":\"high\",\"flexibilityTier\":\"flexible\""+extra+"}")); }
    @Test void draftReadinessAndOrderedEvents() throws Exception {
        long id=commitment("");
        assertEquals("draft",jdbc.queryForObject("SELECT work_state FROM commitments WHERE id=?",String.class,id));
        patchJson("/commitments/"+id,"{\"title\":\"Build\"}").andExpect(jsonPath("$.workState",is("draft")));
        patchJson("/commitments/"+id,"{\"completionCriterion\":\"One endpoint works\"}").andExpect(jsonPath("$.workState",is("ready")));
        assertEquals(java.util.List.of("task.created","task.updated","task.updated","task.ready"),jdbc.queryForList("SELECT type FROM events ORDER BY id",String.class));
        long count=events.count(); patchJson("/commitments/"+id,"{}").andExpect(status().isOk()); assertEquals(count,events.count());
        for(String body:new String[]{"{\"title\":null}","{\"completionCriterion\":\" \"}"}) patchJson("/commitments/"+id,body).andExpect(status().isConflict());
        long ready=commitment(",\"title\":\"Task\",\"completionCriterion\":\"Done\"");
        assertEquals(java.util.List.of("task.created","task.ready"),jdbc.queryForList("SELECT type FROM events WHERE entity_id=? ORDER BY id",String.class,ready));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM events WHERE task_id IS NOT NULL",Integer.class));
    }
    @Test void deadlineOffsetPrecisionAndPatchNull() throws Exception {
        long id=commitment(",\"ownDeadline\":\"2026-09-20T18:30:00.123456789+05:30\"");
        mvc.perform(get("/commitments/"+id).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.ownDeadline",is("2026-09-20T13:00:00.123456Z")));
        assertEquals("2026-09-20T13:00:00.123456",jdbc.queryForObject("SELECT own_deadline FROM commitments WHERE id=?",java.sql.Timestamp.class,id).toLocalDateTime().toString());
        patchJson("/commitments/"+id,"{\"description\":\"preserve\"}").andExpect(jsonPath("$.ownDeadline",is("2026-09-20T13:00:00.123456Z")));
        patchJson("/commitments/"+id,"{\"ownDeadline\":null}").andExpect(jsonPath("$.ownDeadline").doesNotExist());
        for(String value:new String[]{"2026-09-20","2026-09-20T13:00:00","garbage","2026-02-30T12:00:00Z","0999-01-01T00:00:00Z"})
            patchJson("/commitments/"+id,"{\"ownDeadline\":\""+value+"\"}").andExpect(status().isBadRequest());
    }
    @Test void categoryDefaultsExplicitOverrideAndDeleteProtection() throws Exception {
        long cat=created(postJson("/categories","{\"name\":\"Study\",\"color\":\"blue\",\"defaultFlexibilityTier\":\"protected\",\"defaultImportance\":\"medium\"}"));
        long id=created(postJson("/commitments","{\"categoryId\":"+cat+"}"));
        assertEquals("medium",jdbc.queryForObject("SELECT importance FROM commitments WHERE id=?",String.class,id));
        patchJson("/categories/"+cat,"{\"defaultImportance\":\"critical\"}").andExpect(status().isOk());
        patchJson("/commitments/"+id,"{\"categoryId\":null}").andExpect(jsonPath("$.importance",is("medium"))).andExpect(jsonPath("$.flexibilityTier",is("protected")));
        long explicit=created(postJson("/commitments","{\"categoryId\":"+cat+",\"importance\":\"low\"}"));
        assertEquals("low",jdbc.queryForObject("SELECT importance FROM commitments WHERE id=?",String.class,explicit));
        mvc.perform(delete("/categories/"+cat).header("Authorization","Bearer "+token)).andExpect(status().isConflict());
        patchJson("/categories/"+cat,"{\"defaultImportance\":null}").andExpect(status().isOk()).andExpect(jsonPath("$.defaultImportance").doesNotExist());
        postJson("/commitments","{\"categoryId\":"+cat+"}").andExpect(status().isBadRequest());
        patchJson("/categories/"+cat,"{\"defaultImportance\":4}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error_code",is("VALIDATION_ERROR")));
    }
    @Test void ownershipStrictTypesAndNoPublicStateOrProgress() throws Exception {
        long id=commitment("");
        for(String method:new String[]{"GET","PATCH"}) {
            var builder=method.equals("GET")?get("/commitments/"+id):patch("/commitments/"+id).contentType("application/json").content("{}");
            mvc.perform(builder.header("Authorization","Bearer "+foreign)).andExpect(status().isNotFound());
        }
        mvc.perform(get("/commitments/"+id)).andExpect(status().isUnauthorized());
        mvc.perform(post("/commitments").contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/commitments/"+id).contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/commitments/"+id).header("Authorization","Bearer invalid")).andExpect(status().isUnauthorized());
        for(String field:new String[]{"userId","id","workState","work_state","currentCompletionPct","isHardConsequence","userMovedFlag","origin","createdAt"})
            patchJson("/commitments/"+id,"{\""+field+"\":null}").andExpect(status().isBadRequest());
        for(String body:new String[]{"{\"title\":3}","{\"categoryId\":1.5}","{\"categoryId\":\"1\"}","{\"importance\":\"urgent\"}","{\"importance\":null}","{\"flexibilityTier\":\"floating\"}","{\"ownDeadline\":true}"})
            patchJson("/commitments/"+id,body).andExpect(status().isBadRequest());
        jdbc.update("INSERT INTO categories(id,user_id,name,default_flexibility_tier,color) VALUES(901,?,'Private','fixed','red')",other);
        for(long categoryId:new long[]{901,999999}) {
            patchJson("/commitments/"+id,"{\"categoryId\":"+categoryId+"}").andExpect(status().isNotFound());
            postJson("/commitments","{\"importance\":\"high\",\"flexibilityTier\":\"fixed\",\"categoryId\":"+categoryId+"}").andExpect(status().isNotFound());
        }
        mvc.perform(get("/commitments/999999").header("Authorization","Bearer "+token)).andExpect(status().isNotFound()).andExpect(jsonPath("$.error_code",is("COMMITMENT_NOT_FOUND")));
        for(String field:new String[]{"userId","workState","currentCompletionPct"})
            postJson("/commitments","{\"importance\":\"high\",\"flexibilityTier\":\"fixed\",\""+field+"\":null}").andExpect(status().isBadRequest());
        assertEquals(1,events.count());
    }
    @Test void goalMilestoneOwnershipAndConsistency() throws Exception {
        jdbc.update("INSERT INTO goals(id,user_id,title,lifecycle_state,planning_state) VALUES (101,?,'Own','active','active'),(102,?,'Other','active','active'),(103,?,'Second','active','active')",owner,other,owner);
        jdbc.update("INSERT INTO roadmaps(id,goal_id,source) VALUES (101,101,'user_interview'),(102,102,'imported')");
        jdbc.update("INSERT INTO milestones(id,roadmap_id,title,display_order) VALUES (101,101,'Own',0),(102,102,'Other',0)");
        long id=commitment(",\"milestoneId\":101");
        assertEquals(101L,jdbc.queryForObject("SELECT goal_id FROM commitments WHERE id=?",Long.class,id));
        for(String body:new String[]{"{\"goalId\":102}","{\"milestoneId\":102}","{\"milestoneId\":999}"}) patchJson("/commitments/"+id,body).andExpect(status().isNotFound());
        patchJson("/commitments/"+id,"{\"goalId\":103}").andExpect(status().isBadRequest());
        patchJson("/commitments/"+id,"{\"milestoneId\":null,\"goalId\":null}").andExpect(status().isOk()).andExpect(jsonPath("$.goalId").doesNotExist());
        assertEquals("active",jdbc.queryForObject("SELECT planning_state FROM goals WHERE id=101",String.class));
    }
    @Test void legacyLoopRemainsSeparate() throws Exception {
        long legacy=created(postJson("/api/tasks","{\"title\":\"Legacy\"}"));
        commitment("");
        postJson("/api/tasks/"+legacy+"/start","{}").andExpect(status().isOk());
        postJson("/api/tasks/"+legacy+"/finish","{}").andExpect(status().isOk());
        mvc.perform(get("/api/tasks/today").header("Authorization","Bearer "+token)).andExpect(jsonPath("$",hasSize(0)));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM tasks",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM commitments",Integer.class));
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM events WHERE task_id=?",Integer.class,legacy));
    }

    @Test void goalPlanReadsPersistedMilestonesAndPaginatesDirectAndMilestoneTasksWithoutEvents() throws Exception {
        long goal = created(postJson("/goals", "{\"title\":\"Learn piano\"}"));
        mvc.perform(get("/goals/"+goal+"/roadmap").header("Authorization","Bearer "+token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roadmap",nullValue()));
        long roadmap = created(postJson("/goals/"+goal+"/roadmaps", "{}"));
        long milestone = created(postJson("/roadmaps/"+roadmap+"/milestones", "{\"title\":\"First song\",\"order\":1}"));
        long direct = commitment(",\"goalId\":"+goal+",\"title\":\"Choose a song\"");
        long grouped = commitment(",\"milestoneId\":"+milestone+",\"title\":\"Practice\",\"completionCriterion\":\"Play one verse\"");
        commitment(""); // A standalone task must not leak into this goal's plan.
        long count = events.count();
        mvc.perform(get("/goals/"+goal+"/roadmap").header("Authorization","Bearer "+token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roadmap.id",is((int)roadmap)))
                .andExpect(jsonPath("$.roadmap.milestones[0].id",is((int)milestone)));
        mvc.perform(get("/goals/"+goal+"/commitments?limit=1").header("Authorization","Bearer "+token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.commitments",hasSize(1)))
                .andExpect(jsonPath("$.commitments[0].id",is((int)grouped)))
                .andExpect(jsonPath("$.commitments[0].workState",is("ready")))
                .andExpect(jsonPath("$.nextCursor",is((int)grouped)));
        mvc.perform(get("/goals/"+goal+"/commitments?limit=1&cursor="+grouped).header("Authorization","Bearer "+token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.commitments",hasSize(1)))
                .andExpect(jsonPath("$.commitments[0].id",is((int)direct)))
                .andExpect(jsonPath("$.commitments[0].workState",is("draft")))
                .andExpect(jsonPath("$.nextCursor",nullValue()));
        assertEquals(count, events.count());
    }

    @Test void goalPlanReadsRejectForeignMissingUnauthenticatedAndInvalidQueries() throws Exception {
        long goal = created(postJson("/goals", "{\"title\":\"Private\"}"));
        for (String suffix : new String[]{"roadmap", "commitments"}) {
            mvc.perform(get("/goals/"+goal+"/"+suffix)).andExpect(status().isUnauthorized());
            mvc.perform(get("/goals/"+goal+"/"+suffix).header("Authorization","Bearer "+foreign)).andExpect(status().isNotFound());
            mvc.perform(get("/goals/999999/"+suffix).header("Authorization","Bearer "+token)).andExpect(status().isNotFound());
        }
        mvc.perform(get("/goals/"+goal+"/commitments").header("Authorization","Bearer "+token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.commitments",hasSize(0))).andExpect(jsonPath("$.nextCursor",nullValue()));
        for (String query : new String[]{"limit=0", "limit=101", "limit=abc", "cursor=0", "cursor=-1", "cursor=abc"})
            mvc.perform(get("/goals/"+goal+"/commitments?"+query).header("Authorization","Bearer "+token))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error_code",is("VALIDATION_ERROR")));
    }
}
