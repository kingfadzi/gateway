package com.example.onboarding.controller.evidence;

import com.example.onboarding.dto.PageResponse;
import com.example.onboarding.dto.evidence.AttachedDocumentsResponse;
import com.example.onboarding.dto.evidence.CreateEvidenceRequest;
import com.example.onboarding.dto.evidence.EnhancedAttachedDocumentsResponse;
import com.example.onboarding.dto.evidence.CreateEvidenceWithDocumentRequest;
import com.example.onboarding.dto.evidence.Evidence;
import com.example.onboarding.dto.evidence.EvidenceSummary;
import com.example.onboarding.dto.evidence.EvidenceWithDocumentResponse;
import com.example.onboarding.dto.evidence.UpdateEvidenceRequest;
import com.example.onboarding.dto.evidence.AttachEvidenceToFieldRequest;
import com.example.onboarding.dto.evidence.EvidenceFieldLinkResponse;
import com.example.onboarding.dto.evidence.EvidenceUsageResponse;
import com.example.onboarding.service.evidence.EvidenceService;
import com.example.onboarding.service.document.DocumentService;
import com.example.onboarding.dto.document.DocumentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class EvidenceController {
    
    private static final Logger log = LoggerFactory.getLogger(EvidenceController.class);
    
    private final EvidenceService evidenceService;
    private final DocumentService documentService;
    
    public EvidenceController(EvidenceService evidenceService, DocumentService documentService) {
        this.evidenceService = evidenceService;
        this.documentService = documentService;
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
     * POST /api/apps/{appId}/profile/field/{profileFieldId}/attach-document/{documentId}
     */
    @PostMapping("/apps/{appId}/profile/field/{profileFieldId}/attach-document/{documentId}")
    public ResponseEntity<EvidenceWithDocumentResponse> attachDocumentToField(
            @PathVariable String appId,
            @PathVariable String profileFieldId,
            @PathVariable String documentId) {
        log.info("Attaching document {} to profile field {} in app {}", documentId, profileFieldId, appId);
        
        try {
            // Fetch document details
            DocumentResponse document = documentService.getDocumentById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));
            
            EvidenceWithDocumentResponse response = evidenceService.attachDocumentToField(appId, profileFieldId, document);
            log.info("Successfully attached document {} to profile field {} as evidence {}", 
                documentId, profileFieldId, response.evidenceId());
            return ResponseEntity.status(201).body(response);
        } catch (IllegalArgumentException e) {
            log.error("Validation error attaching document {} to profile field {}: {}", documentId, profileFieldId, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Unexpected error attaching document {} to profile field {}: {}", documentId, profileFieldId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Detach a document from a profile field by removing the evidence link
     * DELETE /api/apps/{appId}/profile/field/{profileFieldId}/detach-document/{documentId}
     */
    @DeleteMapping("/apps/{appId}/profile/field/{profileFieldId}/detach-document/{documentId}")
    public ResponseEntity<EvidenceWithDocumentResponse> detachDocumentFromField(
            @PathVariable String appId,
            @PathVariable String profileFieldId,
            @PathVariable String documentId) {
        log.info("Detaching document {} from profile field {} in app {}", documentId, profileFieldId, appId);
        
        try {
            // Fetch document details
            DocumentResponse document = documentService.getDocumentById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));
            
            EvidenceWithDocumentResponse response = evidenceService.detachDocumentFromField(appId, profileFieldId, document);
            log.info("Successfully detached document {} from profile field {}", documentId, profileFieldId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Validation error detaching document {} from profile field {}: {}", documentId, profileFieldId, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Unexpected error detaching document {} from profile field {}: {}", documentId, profileFieldId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Attach evidence to a profile field with workflow management
     * POST /api/evidence/{evidenceId}/attach-to-field/{profileFieldId}
     */
    @PostMapping("/evidence/{evidenceId}/attach-to-field/{profileFieldId}")
    public ResponseEntity<EvidenceFieldLinkResponse> attachEvidenceToField(
            @PathVariable String evidenceId,
            @PathVariable String profileFieldId,
            @RequestParam String appId,
            @RequestBody AttachEvidenceToFieldRequest request) {
        log.info("Attaching evidence {} to profile field {} in app {}", evidenceId, profileFieldId, appId);
        
        try {
            EvidenceFieldLinkResponse response = evidenceService.attachEvidenceToProfileField(
                    evidenceId, profileFieldId, appId, request);
            log.info("Successfully attached evidence {} to profile field {}", evidenceId, profileFieldId);
            return ResponseEntity.status(201).body(response);
        } catch (IllegalArgumentException e) {
            log.error("Validation error attaching evidence {} to field {}: {}", evidenceId, profileFieldId, e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Unexpected error attaching evidence {} to field {}: {}", evidenceId, profileFieldId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Detach evidence from a profile field
     * DELETE /api/evidence/{evidenceId}/detach-from-field/{profileFieldId}
     */
    @DeleteMapping("/evidence/{evidenceId}/detach-from-field/{profileFieldId}")
    public ResponseEntity<Void> detachEvidenceFromField(
            @PathVariable String evidenceId,
            @PathVariable String profileFieldId) {
        log.info("Detaching evidence {} from profile field {}", evidenceId, profileFieldId);
        
        try {
            evidenceService.detachEvidenceFromProfileField(evidenceId, profileFieldId);
            log.info("Successfully detached evidence {} from profile field {}", evidenceId, profileFieldId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            log.error("Validation error detaching evidence {} from field {}: {}", evidenceId, profileFieldId, e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Unexpected error detaching evidence {} from field {}: {}", evidenceId, profileFieldId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get evidence usage across fields and risks
     * GET /api/evidence/{evidenceId}/usage
     */
    @GetMapping("/evidence/{evidenceId}/usage")
    public ResponseEntity<EvidenceUsageResponse> getEvidenceUsage(@PathVariable String evidenceId) {
        log.info("Getting usage information for evidence {}", evidenceId);
        
        try {
            EvidenceUsageResponse response = evidenceService.getEvidenceUsage(evidenceId);
            log.debug("Found {} field usages and {} risk usages for evidence {}", 
                    response.fieldUsages().size(), response.riskUsages().size(), evidenceId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting usage for evidence {}: {}", evidenceId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Review an evidence-field link
     * POST /api/evidence/{evidenceId}/field-link/{profileFieldId}/review
     */
    @PostMapping("/evidence/{evidenceId}/field-link/{profileFieldId}/review")
    public ResponseEntity<EvidenceFieldLinkResponse> reviewEvidenceFieldLink(
            @PathVariable String evidenceId,
            @PathVariable String profileFieldId,
            @RequestParam String reviewedBy,
            @RequestParam String comment,
            @RequestParam boolean approved) {
        log.info("Reviewing evidence field link: {} -> {}, approved: {}", evidenceId, profileFieldId, approved);
        
        try {
            EvidenceFieldLinkResponse response = evidenceService.reviewEvidenceFieldLink(
                    evidenceId, profileFieldId, reviewedBy, comment, approved);
            log.info("Successfully reviewed evidence field link: {} -> {}", evidenceId, profileFieldId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Validation error reviewing evidence field link {} -> {}: {}", evidenceId, profileFieldId, e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Unexpected error reviewing evidence field link {} -> {}: {}", evidenceId, profileFieldId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}