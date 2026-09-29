package com.atlas.backend.ingestion;

import com.atlas.backend.commitment.*;
import com.atlas.backend.event.EventRepository;
import com.atlas.backend.execution.ExecutionException;
import com.atlas.backend.goal.*;
import com.atlas.backend.scheduling.SchedulingPipeline;
import com.atlas.backend.user.*;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.*;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:imports;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class ImportIntegrationTest {
    @Autowired ImportService service;@Autowired UserRepository users;@Autowired GoalService goals;@Autowired JdbcTemplate db;@Autowired SchedulingPipeline scheduler;
    @MockitoBean ScreenshotExtractor ocr;@MockitoSpyBean EventRepository events;
    @Autowired org.springframework.web.context.WebApplicationContext web;@Autowired com.atlas.backend.security.JwtService jwt;
    @Autowired com.atlas.backend.recovery.FixedImportRecoveryService importedRecovery;
    @Autowired CommitmentService commitments;
    @Autowired com.atlas.backend.recovery.RecoveryService recovery;
    @MockitoBean com.atlas.backend.execution.ExecutionClock clock;
    ObjectMapper mapper=new ObjectMapper();long owner;long goal;
    @BeforeEach void setup(){when(clock.now()).thenReturn(Instant.parse("2026-09-28T09:00:00Z"));owner=users.save(User.of(UUID.randomUUID()+"@test.example","test")).getId();goal=goals.create(owner,new CreateGoalRequest("Imported plan",null,null)).id();}
    ImportService.View upload(String text){return mapper.readValue(service.upload(owner,"roadmap",UUID.randomUUID().toString(),"plan.md","text/markdown",text.getBytes(StandardCharsets.UTF_8),null),ImportService.View.class);}
    ImportService.View prepare(){
        var v=upload("# Phase\n- Build compiler\n  - resource: Guide https://example.com\n- optional: Polish\n- prerequisite: Parsing");
        var nodes=v.proposal().nodes().stream().map(n->new ImportNode(n.id(),n.parentId(),n.type(),n.title(),n.text(),n.included(),n.type().equals("task")?"Compiler runs":null,n.importance(),n.flexibilityTier(),n.resourceType(),n.reference(),n.resourceId(),n.startTime(),n.endTime(),n.warning())).toList();
        return mapper.readValue(service.edit(owner,v.id(),"roadmap","edit",new ImportService.Edit(v.revision(),nodes)),ImportService.View.class);
    }
    ImportService.Approval approval(ImportService.View v){return new ImportService.Approval(v.revision(),goal,"medium","flexible");}
    int count(String table){return db.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE user_id=?",Integer.class,owner);}
    @Test void uploadParseAndReviewCreateNoDomainWorkThenExplicitApprovalAndNormalScheduler(){
        var v=prepare();assertEquals(0,count("commitments"));assertEquals(0,count("resources"));assertEquals(0,count("scheduled_blocks"));
        var json=service.approve(owner,v.id(),"roadmap","approve",approval(v));var approved=mapper.readValue(json,ImportService.View.class);
        assertEquals(json,service.approve(owner,v.id(),"roadmap","approve",approval(v)));assertEquals(1,count("commitments"));assertEquals(1,count("resources"));assertEquals(0,count("scheduled_blocks"));
        assertEquals("approved",approved.state());assertEquals(1,approved.result().commitmentIds().size());
        assertEquals(goal,approved.result().goalId());
        assertEquals(goal,service.get(owner,v.id(),"roadmap").result().goalId());
        assertEquals(goal,mapper.readValue(db.queryForObject("SELECT result_json FROM import_proposals WHERE id=?",String.class,v.id()),ImportService.Result.class).goalId());
        db.update("INSERT INTO scheduling_config(user_id,timezone,workable_fraction,buffer_minutes) VALUES(?,'UTC',1,0)",owner);
        for(int day=1;day<=7;day++)db.update("INSERT INTO working_hours_config(user_id,day_of_week,start_time,end_time,kind) VALUES(?,?,'08:00:00','18:00:00','working')",owner,day);
        var scheduled=mapper.readValue(scheduler.generate(owner,"schedule",new SchedulingPipeline.Request(Instant.parse("2026-10-05T08:00:00Z"),Instant.parse("2026-10-05T18:00:00Z"),List.of(new SchedulingPipeline.WorkInput(approved.result().commitmentIds().get(0),30)),null)),SchedulingPipeline.Response.class);
        assertEquals(1,scheduled.placements().size());assertEquals(1,service.get(owner,v.id(),"roadmap").scheduledCount());
        assertThrows(ExecutionException.class,()->service.approve(owner,v.id(),"roadmap","second-approve",approval(v)));
    }
    @Test void staleReviewAndCrossUserReadsEditsApprovalsFail(){
        var v=prepare();long other=users.save(User.of(UUID.randomUUID()+"@test.example","test")).getId();
        assertThrows(ExecutionException.class,()->service.get(other,v.id(),"roadmap"));
        assertThrows(ExecutionException.class,()->service.edit(other,v.id(),"roadmap","foreign",new ImportService.Edit(v.revision(),v.proposal().nodes())));
        assertThrows(ExecutionException.class,()->service.approve(other,v.id(),"roadmap","foreign-approve",approval(v)));
        assertThrows(ExecutionException.class,()->service.approve(owner,v.id(),"roadmap","stale",new ImportService.Approval(0,goal,"medium","flexible")));
        assertEquals(0,count("commitments"));
    }
    @Test void approvalEventFailureRollsBackAllDomainWrites(){
        var v=prepare();EventRepository target=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(events);
        doAnswer(inv->{com.atlas.backend.event.Event e=inv.getArgument(0);if(e.getType().equals("import.approved"))throw new IllegalStateException("Test event failure");return inv.callRealMethod();}).when(target).appendAndFlush(any());
        try{assertThrows(org.springframework.dao.InvalidDataAccessApiUsageException.class,()->service.approve(owner,v.id(),"roadmap","rollback",approval(v)));}finally{reset(target);}
        assertEquals(0,count("commitments"));assertEquals(0,count("resources"));assertEquals("review",service.get(owner,v.id(),"roadmap").state());
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM roadmaps WHERE goal_id=?",Integer.class,goal));
        service.approve(owner,v.id(),"roadmap","rollback",approval(v));assertEquals(1,count("commitments"));
    }
    byte[] image() throws Exception {var out=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(20,20,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",out);return out.toByteArray();}
    @Test void screenshotCandidatesAreNotCalendarFactsUntilReviewed() throws Exception {
        when(ocr.extract(any())).thenReturn(new ScreenshotExtractor.Extraction("Monday 9-10 Class",null));
        var v=mapper.readValue(service.upload(owner,"fixed","image","schedule.png","image/png",image(),null),ImportService.View.class);
        assertEquals(1,v.proposal().nodes().size());assertEquals(0,count("fixed_commitments"));assertEquals(0,count("scheduled_blocks"));
        assertThrows(RuntimeException.class,()->service.approve(owner,v.id(),"fixed","ambiguous",new ImportService.Approval(0,null,null,null)));
        var n=v.proposal().nodes().get(0);var edited=new ImportNode(n.id(),null,"fixed","Class",n.text(),true,null,null,null,null,null,null,"2026-10-01T09:00:00Z","2026-10-01T10:00:00Z",null);
        service.edit(owner,v.id(),"fixed","fixed-edit",new ImportService.Edit(0,List.of(edited)));assertEquals(0,count("fixed_commitments"));
        var a=new ImportService.Approval(1,null,null,null);var result=service.approve(owner,v.id(),"fixed","fixed-approve",a);assertEquals(result,service.approve(owner,v.id(),"fixed","fixed-approve",a));
        assertEquals(1,count("fixed_commitments"));assertEquals("screenshot_import",db.queryForObject("SELECT source FROM fixed_commitments WHERE user_id=?",String.class,owner));assertEquals(0,count("scheduled_blocks"));
        assertNull(service.get(owner,v.id(),"fixed").result().goalId());
    }
    @Test void malformedEmptyAndUnsupportedUploadsFailSafely(){
        assertThrows(IllegalArgumentException.class,()->upload(""));
        assertThrows(IllegalArgumentException.class,()->service.upload(owner,"roadmap","path","../secret.md","text/plain",new byte[]{65},null));
        assertThrows(IllegalArgumentException.class,()->service.upload(owner,"roadmap","binary","bad.md","text/plain",new byte[]{(byte)255},null));
        assertThrows(ExecutionException.class,()->service.upload(owner,"roadmap","pdf","plan.pdf","application/pdf",new byte[]{65},null));
        var empty=upload("   \n");assertTrue(empty.proposal().nodes().isEmpty());assertThrows(IllegalArgumentException.class,()->service.approve(owner,empty.id(),"roadmap","empty",approval(empty)));
    }
    @Test void apiAuthenticationMultipartApprovalAndTenantIsolation() throws Exception {
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(web).apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        var user=users.findById(owner).orElseThrow();String token=jwt.generateToken(user),foreign=jwt.generateToken(users.save(User.of(UUID.randomUUID()+"@test.example","test")));
        var v=prepare();
        for(String path:List.of("/roadmaps/import","/fixed-commitments/import","/resources","/roadmaps/import/"+v.id()))
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/roadmaps/import/"+v.id()).header("Authorization","Bearer "+foreign)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/roadmaps/import/"+v.id()).header("Authorization","Bearer "+foreign).header("Idempotency-Key","foreign-http").contentType("application/json").content(mapper.writeValueAsString(new ImportService.Edit(v.revision(),v.proposal().nodes())))).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/roadmaps/import/"+v.id()+"/approve").header("Authorization","Bearer "+foreign).header("Idempotency-Key","foreign-approve-http").contentType("application/json").content(mapper.writeValueAsString(approval(v)))).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/roadmaps/import/"+v.id()+"/approve").header("Authorization","Bearer "+token).contentType("application/json").content(mapper.writeValueAsString(approval(v)))).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        when(ocr.extract(any())).thenReturn(new ScreenshotExtractor.Extraction("Monday 9-10 Class",null));
        String json=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/fixed-commitments/import").file(new org.springframework.mock.web.MockMultipartFile("file","schedule.png","image/png",image())).header("Authorization","Bearer "+token).header("Idempotency-Key","http-image"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn().getResponse().getContentAsString();
        var image=mapper.readValue(json,ImportService.View.class);assertEquals("fixed",image.kind());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/fixed-commitments/import/"+image.id()+"/approve").header("Authorization","Bearer "+token).header("Idempotency-Key","ambiguous-http").contentType("application/json").content("{\"revision\":0}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
    }
    @Test void approvedScreenshotConflictsUseExistingRecoveryWithoutMovingFixedTime() throws Exception {
        db.update("INSERT INTO scheduling_config(user_id,timezone,workable_fraction,buffer_minutes) VALUES(?,'UTC',1,0)",owner);
        for(int day=1;day<=7;day++)db.update("INSERT INTO working_hours_config(user_id,day_of_week,start_time,end_time,kind) VALUES(?,?,'08:00:00','18:00:00','working')",owner,day);
        var task=commitments.create(owner,mapper.readValue("{\"title\":\"Work\",\"completionCriterion\":\"Done\",\"importance\":\"medium\",\"flexibilityTier\":\"flexible\"}",CommitmentRequest.class));
        db.update("INSERT INTO scheduled_blocks(user_id,commitment_id,start_time,end_time,state,placement_reason) VALUES(?,?,'2026-09-28 10:00:00','2026-09-28 11:00:00','scheduled','Original')",owner,task.id());
        long block=db.queryForObject("SELECT id FROM scheduled_blocks WHERE user_id=?",Long.class,owner);
        var v=mapper.readValue(service.upload(owner,"fixed","conflicting-image","schedule.png","image/png",image(),"Class | 2026-09-28T10:00:00Z | 2026-09-28T11:00:00Z"),ImportService.View.class);
        assertThrows(ExecutionException.class,()->importedRecovery.recover(owner,v.id(),"early",new com.atlas.backend.recovery.FixedImportRecoveryService.Request(1L,List.of())));
        var approved=mapper.readValue(service.approve(owner,v.id(),"fixed","approve-conflict",new ImportService.Approval(0,null,null,null)),ImportService.View.class);
        assertEquals(List.of(block),approved.result().conflictingBlockIds());
        var request=new com.atlas.backend.recovery.FixedImportRecoveryService.Request(approved.result().fixedCommitmentIds().get(0),List.of(new com.atlas.backend.recovery.RecoveryService.Item(block,60)));
        String result=importedRecovery.recover(owner,v.id(),"recover-conflict",request);
        assertEquals(result,importedRecovery.recover(owner,v.id(),"recover-conflict",request));
        // The original manual placement is sticky: existing Recovery correctly requires approval.
        assertEquals("pending",mapper.readTree(result).get("state").asString());
        assertEquals("scheduled",db.queryForObject("SELECT state FROM scheduled_blocks WHERE id=?",String.class,block));
        recovery.respond(owner,mapper.readTree(result).get("id").longValue(),"approve-recovery",true);
        assertEquals("superseded",db.queryForObject("SELECT state FROM scheduled_blocks WHERE id=?",String.class,block));
        assertEquals(LocalDateTime.parse("2026-09-28T10:00:00"),db.queryForObject("SELECT start_time FROM fixed_commitments WHERE user_id=?",LocalDateTime.class,owner));
    }
    @Test void excludedParentDropsDescendantsAndExplicitOptionalOptInPreservesTier(){
        var v=upload("# Removed\n- Build removed\n# Kept\n- optional: Polish\n- prerequisite: Basics");
        var nodes=v.proposal().nodes().stream().map(n->new ImportNode(n.id(),n.parentId(),n.type(),n.title(),n.text(),n.id()==1?false:n.type().equals("optional")||n.included(),"Done",null,null,n.resourceType(),n.reference(),null,null,null,n.warning())).toList();
        service.edit(owner,v.id(),"roadmap","selection-edit",new ImportService.Edit(0,nodes));
        var result=mapper.readValue(service.approve(owner,v.id(),"roadmap","selection-approve",new ImportService.Approval(1,goal,"high","protected")),ImportService.View.class);
        assertEquals(1,result.result().commitmentIds().size());var task=commitments.get(owner,result.result().commitmentIds().get(0));assertEquals("Polish",task.title());assertEquals("optional",task.flexibilityTier());
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM milestones WHERE roadmap_id=?",Integer.class,result.result().roadmapId()));
    }
    @Test void fixedApprovalFailureRollsBackEveryReservationAndLeavesReview() throws Exception {
        var v=mapper.readValue(service.upload(owner,"fixed","two-slots","schedule.png","image/png",image(),"One | 2026-10-01T09:00:00Z | 2026-10-01T10:00:00Z\nTwo | 2026-10-01T11:00:00Z | 2026-10-01T12:00:00Z"),ImportService.View.class);
        EventRepository target=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(events);
        doAnswer(inv->{com.atlas.backend.event.Event event=inv.getArgument(0);if(event.getType().equals("import.approved"))throw new IllegalStateException("Test failure");return inv.callRealMethod();}).when(target).appendAndFlush(any());
        try{assertThrows(org.springframework.dao.InvalidDataAccessApiUsageException.class,()->service.approve(owner,v.id(),"fixed","rollback-fixed",new ImportService.Approval(0,null,null,null)));}finally{reset(target);}
        assertEquals(0,count("fixed_commitments"));assertEquals("review",service.get(owner,v.id(),"fixed").state());
    }
    @Test void historicalResultWithoutGoalIdStillDeserializesAndLoads(){
        var view=prepare();service.approve(owner,view.id(),"roadmap","legacy-approve",approval(view));
        var result=mapper.readTree(db.queryForObject("SELECT result_json FROM import_proposals WHERE id=?",String.class,view.id()));
        ((tools.jackson.databind.node.ObjectNode)result).remove("goalId");
        db.update("UPDATE import_proposals SET result_json=? WHERE id=?",mapper.writeValueAsString(result),view.id());
        var loaded=service.get(owner,view.id(),"roadmap");
        assertNull(loaded.result().goalId());assertNotNull(loaded.result().roadmapId());
        assertEquals("approved",loaded.state());assertEquals(1,loaded.result().commitmentIds().size());
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM goals WHERE user_id=?",Integer.class,owner));
    }
}
