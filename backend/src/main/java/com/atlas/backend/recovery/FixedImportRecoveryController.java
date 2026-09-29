package com.atlas.backend.recovery;

import com.atlas.backend.user.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class FixedImportRecoveryController {
    private final FixedImportRecoveryService service;
    public FixedImportRecoveryController(FixedImportRecoveryService service){this.service=service;}
    @PostMapping(value="/fixed-commitments/import/{id}/recovery",produces="application/json")
    String recover(@AuthenticationPrincipal User u,@PathVariable long id,@RequestHeader("Idempotency-Key") String key,@RequestBody FixedImportRecoveryService.Request request){return service.recover(u.getId(),id,key,request);}
}
