package com.atlas.backend.ingestion;

import com.atlas.backend.user.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping({"/roadmaps/import","/fixed-commitments/import"})
public class ImportController {
    private final ImportService service;
    public ImportController(ImportService service){this.service=service;}
    private String kind(jakarta.servlet.http.HttpServletRequest request){return request.getRequestURI().startsWith("/fixed-commitments/")?"fixed":"roadmap";}
    @GetMapping Object list(@AuthenticationPrincipal User u,jakarta.servlet.http.HttpServletRequest request){return service.list(u.getId(),kind(request));}
    @PostMapping(consumes="multipart/form-data",produces="application/json") String upload(@AuthenticationPrincipal User u,jakarta.servlet.http.HttpServletRequest request,@RequestHeader("Idempotency-Key") String key,@RequestPart MultipartFile file,@RequestParam(required=false) String transcript) throws java.io.IOException {
        return service.upload(u.getId(),kind(request),key,file.getOriginalFilename(),file.getContentType(),file.getBytes(),transcript);
    }
    @GetMapping("/{id}") Object get(@AuthenticationPrincipal User u,jakarta.servlet.http.HttpServletRequest request,@PathVariable long id){return service.get(u.getId(),id,kind(request));}
    @PatchMapping(value="/{id}",produces="application/json") String edit(@AuthenticationPrincipal User u,jakarta.servlet.http.HttpServletRequest request,@PathVariable long id,@RequestHeader("Idempotency-Key") String key,@RequestBody ImportService.Edit edit){return service.edit(u.getId(),id,kind(request),key,edit);}
    @PostMapping(value="/{id}/approve",produces="application/json") String approve(@AuthenticationPrincipal User u,jakarta.servlet.http.HttpServletRequest request,@PathVariable long id,@RequestHeader("Idempotency-Key") String key,@RequestBody ImportService.Approval approval){return service.approve(u.getId(),id,kind(request),key,approval);}
}
