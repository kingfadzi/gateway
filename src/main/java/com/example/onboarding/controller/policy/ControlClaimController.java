package com.example.onboarding.controller.policy;

import com.example.onboarding.dto.policy.ClaimDto;
import com.example.onboarding.dto.policy.CreateClaimRequest;
import com.example.onboarding.service.policy.ControlClaimService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/apps/{appId}/claims")
public class ControlClaimController {

    private final ControlClaimService service;

    public ControlClaimController(ControlClaimService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ClaimDto> create(@PathVariable String appId,
                                           @RequestBody CreateClaimRequest req) {
        var dto = service.createClaim(appId, req);
        return ResponseEntity.ok(dto);
    }
}
