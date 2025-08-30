package com.example.onboarding.controller.evidence;

import com.example.onboarding.dto.PageResponse;
import com.example.onboarding.dto.evidence.AttachDocumentRequest;
import com.example.onboarding.dto.evidence.AttachedDocumentsResponse;
import com.example.onboarding.dto.evidence.CreateEvidenceRequest;
import com.example.onboarding.dto.evidence.EnhancedAttachedDocumentsResponse;
import com.example.onboarding.dto.evidence.CreateEvidenceWithDocumentRequest;
import com.example.onboarding.dto.evidence.Evidence;
import com.example.onboarding.dto.evidence.EvidenceSummary;
import com.example.onboarding.dto.evidence.EvidenceWithDocumentResponse;
import com.example.onboarding.dto.evidence.UpdateEvidenceRequest;
import com.example.onboarding.service.evidence.EvidenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class EvidenceController {
    
    private static final Logger log = LoggerFactory.getLogger(EvidenceController.class);
    
    private final EvidenceService evidenceService;
    
    public EvidenceController(EvidenceService evidenceService) {
        this.evidenceService = evidenceService;
    }
    
    /**
     * Create new evidence for an application
     * POST /api/apps/{appId}/evidence
     */
    @PostMapping("/apps/{appId}/evidence")
    public ResponseEntity<Evidence> createEvidence(@PathVariable String appId,
                                                  @RequestBody CreateEvidenceRequest request) {
        log.info("Creating evidence for app {} with request: {}", appId, request);
        try {
            Evidence evidence = evidenceService.createEvidence(appId, request);
            log.info("Successfully created evidence {} for app {}", evidence.evidenceId(), appId);
            return ResponseEntity.status(201).body(evidence);
        } catch (IllegalArgumentException e) {
            log.error("Validation error creating evidence for app {}: {}", appId, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Unexpected error creating evidence for app {}: {}", appId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Update existing evidence
     * PUT /api/evidence/{evidenceId}
     */
    @PutMapping("/evidence/{evidenceId}")
    public ResponseEntity<Evidence> updateEvidence(@PathVariable String evidenceId,
                                                  @RequestBody UpdateEvidenceRequest request) {
        log.info("Updating evidence {} with request: {}", evidenceId, request);
        try {
            Evidence evidence = evidenceService.updateEvidence(evidenceId, request);
            log.info("Successfully updated evidence {}", evidenceId);
            return ResponseEntity.ok(evidence);
        } catch (IllegalArgumentException e) {
            log.error("Validation error updating evidence {}: {}", evidenceId, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Unexpected error updating evidence {}: {}", evidenceId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get evidence by ID
     * GET /api/evidence/{evidenceId}
     */
    @GetMapping("/evidence/{evidenceId}")
    public ResponseEntity<Evidence> getEvidence(@PathVariable String evidenceId) {
        log.debug("Getting evidence {}", evidenceId);
        return evidenceService.getEvidenceById(evidenceId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * List evidence for an application with pagination
     * GET /api/apps/{appId}/evidence?page=1&pageSize=10
     */
    @GetMapping("/apps/{appId}/evidence")
    public ResponseEntity<PageResponse<EvidenceSummary>> getAppEvidence(
            @PathVariable String appId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        log.debug("Getting evidence for app {} (page={}, pageSize={})", appId, page, pageSize);
        try {
            PageResponse<EvidenceSummary> evidence = evidenceService.getEvidenceByApp(appId, page, pageSize);
            log.debug("Found {} evidence items for app {}", evidence.items().size(), appId);
            return ResponseEntity.ok(evidence);
        } catch (Exception e) {
            log.error("Error getting evidence for app {}: {}", appId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * List evidence for a profile field with pagination
     * GET /api/profile-fields/{profileFieldId}/evidence?page=1&pageSize=10
     */
    @GetMapping("/profile-fields/{profileFieldId}/evidence")
    public ResponseEntity<PageResponse<EvidenceSummary>> getProfileFieldEvidence(
            @PathVariable String profileFieldId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        log.debug("Getting evidence for profile field {} (page={}, pageSize={})", profileFieldId, page, pageSize);
        try {
            PageResponse<EvidenceSummary> evidence = evidenceService.getEvidenceByProfileField(profileFieldId, page, pageSize);
            log.debug("Found {} evidence items for profile field {}", evidence.items().size(), profileFieldId);
            return ResponseEntity.ok(evidence);
        } catch (Exception e) {
            log.error("Error getting evidence for profile field {}: {}", profileFieldId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * List evidence for a claim with pagination
     * GET /api/claims/{claimId}/evidence?page=1&pageSize=10
     */
    @GetMapping("/claims/{claimId}/evidence")
    public ResponseEntity<PageResponse<EvidenceSummary>> getClaimEvidence(
            @PathVariable String claimId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        log.debug("Getting evidence for claim {} (page={}, pageSize={})", claimId, page, pageSize);
        try {
            PageResponse<EvidenceSummary> evidence = evidenceService.getEvidenceByClaim(claimId, page, pageSize);
            log.debug("Found {} evidence items for claim {}", evidence.items().size(), claimId);
            return ResponseEntity.ok(evidence);
        } catch (Exception e) {
            log.error("Error getting evidence for claim {}: {}", claimId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * List evidence for a track with pagination
     * GET /api/tracks/{trackId}/evidence?page=1&pageSize=10
     */
    @GetMapping("/tracks/{trackId}/evidence")
    public ResponseEntity<PageResponse<EvidenceSummary>> getTrackEvidence(
            @PathVariable String trackId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        log.debug("Getting evidence for track {} (page={}, pageSize={})", trackId, page, pageSize);
        try {
            PageResponse<EvidenceSummary> evidence = evidenceService.getEvidenceByTrack(trackId, page, pageSize);
            log.debug("Found {} evidence items for track {}", evidence.items().size(), trackId);
            return ResponseEntity.ok(evidence);
        } catch (Exception e) {
            log.error("Error getting evidence for track {}: {}", trackId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Revoke evidence
     * POST /api/evidence/{evidenceId}/revoke
     */
    @PostMapping("/evidence/{evidenceId}/revoke")
    public ResponseEntity<Evidence> revokeEvidence(@PathVariable String evidenceId,
                                                  @RequestParam String reviewedBy) {
        log.info("Revoking evidence {} by {}", evidenceId, reviewedBy);
        try {
            Evidence evidence = evidenceService.revokeEvidence(evidenceId, reviewedBy);
            log.info("Successfully revoked evidence {} by {}", evidenceId, reviewedBy);
            return ResponseEntity.ok(evidence);
        } catch (IllegalArgumentException e) {
            log.error("Validation error revoking evidence {}: {}", evidenceId, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Unexpected error revoking evidence {}: {}", evidenceId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Create evidence with a new document in a single operation
     * POST /api/apps/{appId}/evidence/with-document
     */
    @PostMapping("/apps/{appId}/evidence/with-document")
    public ResponseEntity<EvidenceWithDocumentResponse> createEvidenceWithDocument(@PathVariable String appId,
                                                                                   @RequestBody CreateEvidenceWithDocumentRequest request) {
        log.info("Creating evidence with document for app {} with request: {}", appId, request);
        try {
            EvidenceWithDocumentResponse response = evidenceService.createEvidenceWithDocument(appId, request);
            log.info("Successfully created evidence {} with document {} for app {}", 
                response.evidenceId(), response.document().documentId(), appId);
            return ResponseEntity.status(201).body(response);
        } catch (IllegalArgumentException e) {
            log.error("Validation error creating evidence with document for app {}: {}", appId, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Unexpected error creating evidence with document for app {}: {}", appId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get all documents currently attached as evidence to a profile field
     * GET /api/apps/{appId}/profile/field/{profileFieldId}/attached-documents
     */
    @GetMapping("/apps/{appId}/profile/field/{profileFieldId}/attached-documents")
    public ResponseEntity<EnhancedAttachedDocumentsResponse> getAttachedDocuments(
            @PathVariable String appId,
            @PathVariable String profileFieldId,
            @RequestParam(defaultValue = "false") boolean enhanced) {
        log.info("Getting {} attached documents for profile field {} in app {}", 
            enhanced ? "enhanced" : "basic", profileFieldId, appId);
        
        try {
            EnhancedAttachedDocumentsResponse response = evidenceService.getEnhancedAttachedDocuments(appId, profileFieldId);
            log.debug("Found {} attached documents for profile field {}", response.documents().size(), profileFieldId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting attached documents for profile field {} in app {}: {}", profileFieldId, appId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Attach an existing document as evidence to a profile field
     * POST /api/apps/{appId}/profile/field/{profileFieldId}/attach-document
     */
    @PostMapping("/apps/{appId}/profile/field/{profileFieldId}/attach-document")
    public ResponseEntity<Evidence> attachDocumentToField(
            @PathVariable String appId,
            @PathVariable String profileFieldId,
            @RequestBody AttachDocumentRequest request) {
        log.info("Attaching document {} to profile field {} in app {}", request.documentId(), profileFieldId, appId);
        
        try {
            Evidence evidence = evidenceService.attachDocumentToField(appId, profileFieldId, request);
            log.info("Successfully attached document {} to profile field {} as evidence {}", 
                request.documentId(), profileFieldId, evidence.evidenceId());
            return ResponseEntity.status(201).body(evidence);
        } catch (IllegalArgumentException e) {
            log.error("Validation error attaching document {} to profile field {}: {}", request.documentId(), profileFieldId, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Unexpected error attaching document {} to profile field {}: {}", request.documentId(), profileFieldId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Detach a document from a profile field by removing the evidence link
     * DELETE /api/apps/{appId}/profile/field/{profileFieldId}/detach-document/{documentId}
     */
    @DeleteMapping("/apps/{appId}/profile/field/{profileFieldId}/detach-document/{documentId}")
    public ResponseEntity<Void> detachDocumentFromField(
            @PathVariable String appId,
            @PathVariable String profileFieldId,
            @PathVariable String documentId) {
        log.info("Detaching document {} from profile field {} in app {}", documentId, profileFieldId, appId);
        
        try {
            evidenceService.detachDocumentFromField(appId, profileFieldId, documentId);
            log.info("Successfully detached document {} from profile field {}", documentId, profileFieldId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            log.error("Validation error detaching document {} from profile field {}: {}", documentId, profileFieldId, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Unexpected error detaching document {} from profile field {}: {}", documentId, profileFieldId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}