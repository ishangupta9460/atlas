package com.atlas.backend.resource;

import com.atlas.backend.commitment.*;
import com.atlas.backend.user.*;
import com.atlas.backend.event.EventRepository;
import com.atlas.backend.execution.ExecutionException;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:resources;MODE=MySQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class ResourceIntegrationTest {
    @Autowired ResourceService service;
    @Autowired CommitmentService tasks;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate db;
    @MockitoSpyBean EventRepository events;
    @Autowired org.springframework.web.context.WebApplicationContext web;
    @Autowired com.atlas.backend.security.JwtService jwt;
    ObjectMapper mapper=new ObjectMapper(); long owner;
    @BeforeEach void setup(){owner=users.save(User.of(UUID.randomUUID()+"@test.example","test")).getId();}
    long task(){return tasks.create(owner,mapper.readValue("{\"title\":\"Build\",\"completionCriterion\":\"Works\",\"importance\":\"medium\",\"flexibilityTier\":\"flexible\"}",CommitmentRequest.class)).id();}
    long resource(){return mapper.readTree(service.create(owner,UUID.randomUUID().toString(),new ResourceService.Input("link","Guide","https://example.com"))).get("id").longValue();}
    @Test void manyToManyReplacementChangesOnlyAttachmentsAndReplays(){
        long a=task(),b=task(),r=resource(),next=resource();var before=tasks.get(owner,a);
        service.attachment(owner,a,r,null,false,"a");service.attachment(owner,b,r,null,false,"b");
        service.attachment(owner,a,next,null,false,"next");
        String swapped=service.attachment(owner,a,r,next,false,"swap");
        assertEquals(swapped,service.attachment(owner,a,r,next,false,"swap"));assertEquals(before,tasks.get(owner,a));
        assertEquals(List.of(next),service.attachments(owner,a).stream().map(ResourceService.Resource::id).toList());
        assertEquals(r,service.attachments(owner,b).get(0).id());assertEquals(2,service.list(owner).size());
        assertThrows(ExecutionException.class,()->service.delete(owner,r,"delete"));
    }
    @Test void feedbackIsHistoryOnlyAndRetryIsSingleReaction(){
        long r=resource();var input=new ResourceService.Feedback("disliked","Too long");
        var result=service.feedback(owner,r,"feedback",input);assertEquals(result,service.feedback(owner,r,"feedback",input));
        assertEquals(1,service.feedbackHistory(owner,r).size());assertFalse(mapper.readTree(result).get("preferenceCreated").booleanValue());
        service.feedback(owner,r,"another",input);assertEquals(2,service.feedbackHistory(owner,r).size());
    }
    @Test void ownerIsolationAndDatabaseConstraints(){
        long t=task(),r=resource(),other=users.save(User.of(UUID.randomUUID()+"@test.example","test")).getId();
        assertEquals(404,assertThrows(ExecutionException.class,()->service.get(other,r)).status());
        assertThrows(ExecutionException.class,()->service.attachment(other,t,r,null,false,"foreign"));
        assertThrows(ExecutionException.class,()->service.feedback(other,r,"foreign-feedback",new ResourceService.Feedback("liked",null)));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->db.update("INSERT INTO task_resource(user_id,commitment_id,resource_id) VALUES(?,?,?)",other,t,r));
    }
    @Test void eventFailureRollsBackReplacementAndReplayKey(){
        long t=task(),r=resource(),next=resource();service.attachment(owner,t,r,null,false,"attach");
        EventRepository target=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(events);
        doThrow(new IllegalStateException("test event failure")).when(target).appendAndFlush(any());
        try {assertThrows(org.springframework.dao.InvalidDataAccessApiUsageException.class,()->service.attachment(owner,t,r,next,false,"failed"));}
        finally {reset(target);}
        assertEquals(r,service.attachments(owner,t).get(0).id());
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM execution_idempotency WHERE user_id=? AND request_key='failed'",Integer.class,owner));
    }
    @Test void invalidReferencesAndChangedReplayPayloadFail(){
        assertThrows(IllegalArgumentException.class,()->service.create(owner,"unsafe",new ResourceService.Input("link","Bad","javascript:alert(1)")));
        var input=new ResourceService.Input("book","Book","ISBN 123");service.create(owner,"same",input);
        assertThrows(ExecutionException.class,()->service.create(owner,"same",new ResourceService.Input("book","Other","ISBN 456")));
    }
    @Test void everyResourceRouteAuthenticatesAndRejectsForeignEntities() throws Exception {
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(web).apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        String token=jwt.generateToken(users.save(User.of(UUID.randomUUID()+"@test.example","test")));
        long task=task(),resource=resource();
        var routes=List.of(new String[]{"GET","/resources/"+resource,""},new String[]{"PATCH","/resources/"+resource,"{\"type\":\"link\",\"title\":\"Guide\",\"urlOrFileRef\":\"https://example.com\"}"},new String[]{"DELETE","/resources/"+resource,""},
            new String[]{"GET","/commitments/"+task+"/resources",""},new String[]{"POST","/commitments/"+task+"/resources","{\"resourceId\":"+resource+"}"},new String[]{"DELETE","/commitments/"+task+"/resources/"+resource,""},
            new String[]{"POST","/commitments/"+task+"/resources/"+resource+"/replace","{\"resourceId\":"+resource+"}"},new String[]{"POST","/resources/"+resource+"/feedback","{\"reaction\":\"liked\"}"},new String[]{"GET","/resources/"+resource+"/feedback",""});
        for(var route:routes){
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(route[0]),route[1]).contentType("application/json").content(route[2])).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(route[0]),route[1]).header("Authorization","Bearer "+token).header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json").content(route[2])).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
        }
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/resources/feedback-policy").header("Authorization","Bearer "+token)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.patternDetectionEnabled").value(false)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.preferenceConfirmationAvailable").value(false));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/resources").header("Authorization","Bearer "+token).contentType("application/json").content("{}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
    }
}
