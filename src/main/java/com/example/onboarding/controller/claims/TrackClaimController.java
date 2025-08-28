package com.example.onboarding.controller.claims;

import com.example.onboarding.dto.PageResponse;
import com.example.onboarding.dto.claims.AttachEvidenceRequest;
import com.example.onboarding.dto.claims.ClaimSummary;
import com.example.onboarding.dto.claims.ClaimWithEvidence;
import com.example.onboarding.dto.claims.EnsureClaimRequest;
import com.example.onboarding.dto.evidence.Evidence;
import com.example.onboarding.service.claims.TrackClaimService;
import com.example.onboarding.service.evidence.EvidenceClaimService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class TrackClaimController {
    
    private static final Logger log = LoggerFactory.getLogger(TrackClaimController.class);
    private final TrackClaimService trackClaimService;
    private final EvidenceClaimService evidenceClaimService;
    
    public TrackClaimController(TrackClaimService trackClaimService, EvidenceClaimService evidenceClaimService) {
        this.trackClaimService = trackClaimService;
        this.evidenceClaimService = evidenceClaimService;
    }
    
    /**
     * Ensure a claim exists for a given profile field and track
     * POST/PUT /api/apps/{appId}/tracks/{trackId}/claims/ensure
     */
    @PostMapping("/apps/{appId}/tracks/{trackId}/claims/ensure")
    public ResponseEntity<ClaimSummary> ensureClaim(@PathVariable String appId,
                                                   @PathVariable String trackId,
                                                   @RequestBody EnsureClaimRequest request) {
        log.info("Ensuring claim exists for app {} track {} with request: {}", appId, trackId, request);
        try {
            ClaimSummary claim = trackClaimService.ensureClaim(appId, trackId, request);
            log.info("Successfully ensured claim {} for app {} track {}", claim.claimId(), appId, trackId);
            return ResponseEntity.ok(claim);
        } catch (IllegalArgumentException e) {
            log.error("Validation error ensuring claim for app {} track {}: {}", appId, trackId, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Unexpected error ensuring claim for app {} track {}: {}", appId, trackId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Alternative PUT endpoint for idempotent behavior
     * PUT /api/apps/{appId}/tracks/{trackId}/claims/ensure
     */
    @PutMapping("/apps/{appId}/tracks/{trackId}/claims/ensure")
    public ResponseEntity<ClaimSummary> ensureClaimIdempotent(@PathVariable String appId,
                                                             @PathVariable String trackId,
                                                             @RequestBody EnsureClaimRequest request) {
        log.debug("PUT ensure claim - delegating to POST logic");
        return ensureClaim(appId, trackId, request);
    }
    
    /**
     * Get claim with all attached evidence
     * GET /api/claims/{claimId}
     */
    @GetMapping("/claims/{claimId}")
    public ResponseEntity<ClaimWithEvidence> getClaim(@PathVariable String claimId) {
        return trackClaimService.getClaimWithEvidence(claimId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * List claims for a track with pagination
     * GET /api/tracks/{trackId}/claims?page=1&pageSize=10
     */
    @GetMapping("/tracks/{trackId}/claims")
    public ResponseEntity<PageResponse<ClaimSummary>> getTrackClaims(
            @PathVariable String trackId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        log.debug("Getting claims for track {} (page={}, pageSize={})", trackId, page, pageSize);
        try {
            PageResponse<ClaimSummary> claims = trackClaimService.getClaimsByTrack(trackId, page, pageSize);
            log.debug("Found {} claims for track {}", claims.items().size(), trackId);
            return ResponseEntity.ok(claims);
        } catch (IllegalArgumentException e) {
            log.error("Validation error getting claims for track {}: {}", trackId, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Unexpected error getting claims for track {}: {}", trackId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Attach evidence to a claim with optional document references
     * POST /api/claims/{claimId}/evidence/{evidenceId}/attach
     */
    @PostMapping("/claims/{claimId}/evidence/{evidenceId}/attach")
    public ResponseEntity<Evidence> attachEvidence(@PathVariable String claimId,
                                                  @PathVariable String evidenceId,
                                                  @RequestBody AttachEvidenceRequest request) {
        try {
            Evidence evidence = evidenceClaimService.attachEvidenceToClaim(claimId, evidenceId, request);
            return ResponseEntity.ok(evidence);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Detach evidence from a claim
     * DELETE /api/evidence/{evidenceId}/claim
     */
    @DeleteMapping("/evidence/{evidenceId}/claim")
    public ResponseEntity<Evidence> detachEvidence(@PathVariable String evidenceId) {
        try {
            Evidence evidence = evidenceClaimService.detachEvidenceFromClaim(evidenceId);
            return ResponseEntity.ok(evidence);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}