package com.atlas.backend.resource;

import com.atlas.backend.user.User;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import tools.jackson.databind.JsonNode;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class ResourceController {
    private final ResourceService service;
    public ResourceController(ResourceService service){this.service=service;}
    public record Attachment(Long resourceId) {
        @JsonAnySetter public void unknown(String k,JsonNode v){throw new IllegalArgumentException("Unknown attachment field");}
        long required(){if(resourceId==null || resourceId<=0)throw new IllegalArgumentException("Select a resource");return resourceId;}
    }
    @GetMapping("/resources") Object list(@AuthenticationPrincipal User u){return service.list(u.getId());}
    @GetMapping("/resources/feedback-policy") Object policy(@AuthenticationPrincipal User u){return java.util.Map.of("patternDetectionEnabled",false,"preferenceConfirmationAvailable",false,"reason","Pattern policy awaits product approval; preference storage belongs to Chunk 7.");}
    @GetMapping("/resources/{id}") Object get(@AuthenticationPrincipal User u,@PathVariable long id){return service.get(u.getId(),id);}
    @PostMapping(value="/resources",produces="application/json") String create(@AuthenticationPrincipal User u,@RequestHeader("Idempotency-Key") String key,@RequestBody ResourceService.Input input){return service.create(u.getId(),key,input);}
    @PatchMapping(value="/resources/{id}",produces="application/json") String update(@AuthenticationPrincipal User u,@PathVariable long id,@RequestHeader("Idempotency-Key") String key,@RequestBody ResourceService.Input input){return service.update(u.getId(),id,key,input);}
    @DeleteMapping(value="/resources/{id}",produces="application/json") String delete(@AuthenticationPrincipal User u,@PathVariable long id,@RequestHeader("Idempotency-Key") String key){return service.delete(u.getId(),id,key);}
    @GetMapping("/commitments/{task}/resources") Object attachments(@AuthenticationPrincipal User u,@PathVariable long task){return service.attachments(u.getId(),task);}
    @PostMapping(value="/commitments/{task}/resources",produces="application/json") String attach(@AuthenticationPrincipal User u,@PathVariable long task,@RequestHeader("Idempotency-Key") String key,@RequestBody Attachment input){return service.attachment(u.getId(),task,input.required(),null,false,key);}
    @DeleteMapping(value="/commitments/{task}/resources/{id}",produces="application/json") String detach(@AuthenticationPrincipal User u,@PathVariable long task,@PathVariable long id,@RequestHeader("Idempotency-Key") String key){return service.attachment(u.getId(),task,id,null,true,key);}
    @PostMapping(value="/commitments/{task}/resources/{id}/replace",produces="application/json") String replace(@AuthenticationPrincipal User u,@PathVariable long task,@PathVariable long id,@RequestHeader("Idempotency-Key") String key,@RequestBody Attachment input){return service.attachment(u.getId(),task,id,input.required(),false,key);}
    @PostMapping(value="/resources/{id}/feedback",produces="application/json") String feedback(@AuthenticationPrincipal User u,@PathVariable long id,@RequestHeader("Idempotency-Key") String key,@RequestBody ResourceService.Feedback input){return service.feedback(u.getId(),id,key,input);}
    @GetMapping("/resources/{id}/feedback") Object history(@AuthenticationPrincipal User u,@PathVariable long id){return service.feedbackHistory(u.getId(),id);}
}
