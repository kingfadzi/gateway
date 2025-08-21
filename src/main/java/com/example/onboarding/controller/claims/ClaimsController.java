// src/main/java/com/example/onboarding/web/ClaimsController.java
package com.example.onboarding.controller.claims;

import com.example.onboarding.dto.claims.ClaimDecision;
import com.example.onboarding.dto.claims.CreateClaimRequest;
import com.example.onboarding.service.claims.ClaimsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/apps/{appId}/claims")
public class ClaimsController {

  private final ClaimsService service;

  public ClaimsController(ClaimsService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<ClaimDecision> create(
      @PathVariable String appId,
      @RequestBody CreateClaimRequest req
  ) {
    return ResponseEntity.ok(service.evaluateAndCreate(appId, req));
  }
}
