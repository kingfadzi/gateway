package com.example.gateway.evidence.controller;

import com.example.gateway.evidence.dto.*;
import com.example.gateway.application.dto.PageResponse;
import com.example.gateway.evidence.service.EvidenceService;
import com.example.gateway.evidence.service.EvidenceOrchestrationService;
import com.example.gateway.document.service.DocumentService;
import com.example.gateway.evidence.service.EvidenceFieldLinkService;
import com.example.gateway.evidence.service.EvidenceAttestationService;
import com.example.gateway.document.dto.DocumentResponse;
import dev.controlplane.auditkit.annotations.Audited;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class EvidenceController {
    
    private static final Logger log = LoggerFactory.getLogger(EvidenceController.class);
    
    private final EvidenceService evidenceService;
    private final EvidenceOrchestrationService evidenceOrchestrationService;
    private final DocumentService documentService;
    private final EvidenceFieldLinkService evidenceFieldLinkService;
    private final EvidenceAttestationService evidenceAttestationService;

    public EvidenceController(EvidenceService evidenceService,
                 EvidenceOrchestrationService evidenceOrchestrationService,
                 DocumentService documentService,
                 EvidenceFieldLinkService evidenceFieldLinkService,
                 EvidenceAttestationService evidenceAttestationService) {
        this.evidenceService = evidenceService;
        this.evidenceOrchestrationService = evidenceOrchestrationService;
        this.documentService = documentService;
        this.evidenceFieldLinkService = evidenceFieldLinkService;
        this.evidenceAttestationService = evidenceAttestationService;
    }
    
    /**
     * Create new evidence for an application
     * POST /api/apps/{appId}/evidence
     */
    @PostMapping("/apps/{appId}/evidence")
    public ResponseEntity<Evidence> createEvidence(@PathVariable String appId,
                                      @RequestBody CreateEvidenceRequest request) {
        log.info("Creating evidence for app {}", appId);
        Evidence evidence = evidenceService.createEvidence(appId, request);
        log.info("Successfully created evidence {} for app {}", evidence.evidenceId(), appId);
        return ResponseEntity.status(201).body(evidence);
    }
    
    /**
     * Update existing evidence
     * PUT /api/evidence/{evidenceId}
     */
    @PutMapping("/evidence/{evidenceId}")
    public ResponseEntity<Evidence> updateEvidence(@PathVariable String evidenceId,
                                      @RequestBody UpdateEvidenceRequest request) {
        log.info("Updating evidence {}", evidenceId);
        Evidence evidence = evidenceService.updateEvidence(evidenceId, request);
        log.info("Successfully updated evidence {}", evidenceId);
        return ResponseEntity.ok(evidence);
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
    public ResponseEntity<PageResponse<EnhancedEvidenceSummary>> getAppEvidence(
            @PathVariable String appId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        log.debug("Getting evidence for app {} (page={}, pageSize={})", appId, page, pageSize);
        PageResponse<EnhancedEvidenceSummary> evidence = evidenceService.getEvidenceByApp(appId, page, pageSize);
        log.debug("Found {} evidence items for app {}", evidence.items().size(), appId);
        return ResponseEntity.ok(evidence);
    }
    
    /**
     * List evidence for a profile field with pagination (enhanced with EvidenceFieldLink metadata)
     * GET /api/profile-fields/{profileFieldId}/evidence?page=1&pageSize=10
     */
    @GetMapping("/profile-fields/{profileFieldId}/evidence")
    public ResponseEntity<PageResponse<EnhancedEvidenceSummary>> getProfileFieldEvidence(
            @PathVariable String profileFieldId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        log.debug("Getting enhanced evidence for profile field {} (page={}, pageSize={})", profileFieldId, page, pageSize);
        PageResponse<EnhancedEvidenceSummary> evidence = evidenceService.getEvidenceByProfileField(profileFieldId, page, pageSize);
        log.debug("Found {} enhanced evidence items for profile field {}", evidence.items().size(), profileFieldId);
        return ResponseEntity.ok(evidence);
    }
    
    /**
     * List evidence for a claim with pagination
     * GET /api/claims/{claimId}/evidence?page=1&pageSize=10
     */
    @GetMapping("/claims/{claimId}/evidence")
    public ResponseEntity<PageResponse<EnhancedEvidenceSummary>> getClaimEvidence(
            @PathVariable String claimId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        log.debug("Getting evidence for claim {} (page={}, pageSize={})", claimId, page, pageSize);
        PageResponse<EnhancedEvidenceSummary> evidence = evidenceService.getEvidenceByClaim(claimId, page, pageSize);
        log.debug("Found {} evidence items for claim {}", evidence.items().size(), claimId);
        return ResponseEntity.ok(evidence);
    }
    
    /**
     * List evidence for a track with pagination
     * GET /api/tracks/{trackId}/evidence?page=1&pageSize=10
     */
    @GetMapping("/tracks/{trackId}/evidence")
    public ResponseEntity<PageResponse<EnhancedEvidenceSummary>> getTrackEvidence(
            @PathVariable String trackId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        log.debug("Getting evidence for track {} (page={}, pageSize={})", trackId, page, pageSize);
        PageResponse<EnhancedEvidenceSummary> evidence = evidenceService.getEvidenceByTrack(trackId, page, pageSize);
        log.debug("Found {} evidence items for track {}", evidence.items().size(), trackId);
        return ResponseEntity.ok(evidence);
    }
    
    /**
     * Revoke evidence
     * POST /api/evidence/{evidenceId}/revoke
     */
    @PostMapping("/evidence/{evidenceId}/revoke")
    public ResponseEntity<Evidence> revokeEvidence(@PathVariable String evidenceId,
                                      @RequestParam String reviewedBy) {
        log.info("Revoking evidence {} by {}", evidenceId, reviewedBy);
        Evidence evidence = evidenceService.revokeEvidence(evidenceId, reviewedBy);
        log.info("Successfully revoked evidence {} by {}", evidenceId, reviewedBy);
        return ResponseEntity.ok(evidence);
    }
    
    /**
     * Create evidence with a new document in a single operation
     * POST /api/apps/{appId}/evidence/with-document
     */
    @PostMapping("/apps/{appId}/evidence/with-document")
    public ResponseEntity<EvidenceWithDocumentResponse> createEvidenceWithDocument(@PathVariable String appId,
                                                                       @RequestBody CreateEvidenceWithDocumentRequest request) {
        log.info("Creating evidence with document for app {} with request: {}", appId, request);
        EvidenceWithDocumentResponse response = evidenceOrchestrationService.createEvidenceWithDocument(appId, request);
        log.info("Successfully created evidence {} with document {} for app {}", 
                response.evidenceId(), response.document().documentId(), appId);
        return ResponseEntity.status(201).body(response);
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
        
        EnhancedAttachedDocumentsResponse response = evidenceService.getEnhancedAttachedDocuments(appId, profileFieldId);
        log.debug("Found {} attached documents for profile field {}", response.documents().size(), profileFieldId);
        return ResponseEntity.ok(response);
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
        
        // Fetch document details
        DocumentResponse document = documentService.getDocumentById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));
            
        EvidenceWithDocumentResponse response = evidenceService.attachDocumentToField(appId, profileFieldId, document);
        log.info("Successfully attached document {} to profile field {} as evidence {}", 
                documentId, profileFieldId, response.evidenceId());
        return ResponseEntity.status(201).body(response);
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
        
        // Fetch document details
        DocumentResponse document = documentService.getDocumentById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));
            
        EvidenceWithDocumentResponse response = evidenceService.detachDocumentFromField(appId, profileFieldId, document);
        log.info("Successfully detached document {} from profile field {}", documentId, profileFieldId);
        return ResponseEntity.ok(response);
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
        
        EvidenceFieldLinkResponse response = evidenceService.attachEvidenceToProfileField(
        evidenceId, profileFieldId, appId, request);
        log.info("Successfully attached evidence {} to profile field {}", evidenceId, profileFieldId);
        return ResponseEntity.status(201).body(response);
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
        
        evidenceService.detachEvidenceFromProfileField(evidenceId, profileFieldId);
        log.info("Successfully detached evidence {} from profile field {}", evidenceId, profileFieldId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Get evidence usage across fields and risks
     * GET /api/evidence/{evidenceId}/usage
     */
    @GetMapping("/evidence/{evidenceId}/usage")
    public ResponseEntity<EvidenceUsageResponse> getEvidenceUsage(@PathVariable String evidenceId) {
        log.info("Getting usage information for evidence {}", evidenceId);
        
        EvidenceUsageResponse response = evidenceService.getEvidenceUsage(evidenceId);
        log.debug("Found {} field usages and {} risk usages for evidence {}", 
        response.fieldUsages().size(), response.riskUsages().size(), evidenceId);
        return ResponseEntity.ok(response);
    }

    /**
     * Search evidence with enhanced filtering and optional workbench enrichment
     * GET /api/evidence/search?linkStatus=PENDING_PO_REVIEW&appId=APM100001&fieldKey=backup_policy&enhanced=true
     */
    @GetMapping("/evidence/search")
    public ResponseEntity<?> searchEvidence(
            @RequestParam(required = false) String linkStatus,
            @RequestParam(required = false) String appId,
            @RequestParam(required = false) String fieldKey,
            @RequestParam(required = false) String assignedPo,
            @RequestParam(required = false) String assignedSme,
            @RequestParam(required = false) String evidenceStatus,
            @RequestParam(required = false) String documentSourceType,
            @RequestParam(required = false) String criticality,
            @RequestParam(required = false) String applicationType,
            @RequestParam(required = false) String architectureType,
            @RequestParam(required = false) String installType,
            @RequestParam(required = false) String assignedReviewer,
            @RequestParam(required = false) String submittedBy,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "false") boolean enhanced,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        log.debug("Searching evidence with filters: linkStatus={}, appId={}, fieldKey={}, enhanced={}",
                 linkStatus, appId, fieldKey, enhanced);
        if (enhanced) {
                // Create request object for workbench-style search
                EvidenceSearchRequest request = new EvidenceSearchRequest();
                request.setApprovalStatus(linkStatus);
                request.setAppId(appId);
                request.setFieldKey(fieldKey);
                request.setAssignedReviewer(assignedReviewer != null ? assignedReviewer : assignedPo);
                request.setSubmittedBy(submittedBy);
                request.setCriticality(criticality);
                request.setApplicationType(applicationType);
                request.setArchitectureType(architectureType);
                request.setInstallType(installType);
                request.setDomain(domain);
                request.setSearch(search);
                request.setLimit(size);
                request.setOffset(page * size);

                List<WorkbenchEvidenceItem> evidence = evidenceService.searchWorkbenchEvidence(request);
                log.debug("Found {} enhanced evidence items matching search criteria", evidence.size());
                return ResponseEntity.ok(evidence);
            } else {
                List<EnhancedEvidenceSummary> evidence = evidenceService.searchEvidence(
        linkStatus, appId, fieldKey, assignedPo, assignedSme, evidenceStatus, documentSourceType, page, size);
                log.debug("Found {} evidence items matching search criteria", evidence.size());
                return ResponseEntity.ok(evidence);
            }
    }

    /**
     * Get pending PO review evidence for a Product Owner
     * GET /api/evidence/pending-po-review?assignedPo=po@company.com
     */
    @GetMapping("/evidence/pending-po-review")
    public ResponseEntity<List<EnhancedEvidenceSummary>> getPendingPoReviewEvidence(
            @RequestParam String assignedPo) {
        log.debug("Getting pending PO review evidence for: {}", assignedPo);
        // Use the search method with linkStatus filter
        List<EnhancedEvidenceSummary> evidence = evidenceService.searchEvidence(
                "PENDING_PO_REVIEW", null, null, assignedPo, null, null, null, 0, 100);
        log.debug("Found {} evidence items pending PO review", evidence.size());
        return ResponseEntity.ok(evidence);
    }

    /**
     * Get pending SME review evidence
     * GET /api/evidence/pending-sme-review?assignedSme=sme@company.com
     */
    @GetMapping("/evidence/pending-sme-review")
    public ResponseEntity<List<EnhancedEvidenceSummary>> getPendingSmeReviewEvidence(
            @RequestParam String assignedSme) {
        log.debug("Getting pending SME review evidence for: {}", assignedSme);
        // Use the search method with linkStatus filter
        List<EnhancedEvidenceSummary> evidence = evidenceService.searchEvidence(
                "PENDING_SME_REVIEW", null, null, null, assignedSme, null, null, 0, 100);
        log.debug("Found {} evidence items pending SME review", evidence.size());
        return ResponseEntity.ok(evidence);
    }

    /**
     * Approve evidence as Product Owner (for low criticality fields)
     * POST /api/evidence/{evidenceId}/po-approve
     */
    @PostMapping("/evidence/{evidenceId}/po-approve")
    public ResponseEntity<EvidenceFieldLinkResponse> approveEvidenceAsPo(
            @PathVariable String evidenceId,
            @RequestParam String profileFieldId,
            @RequestParam String reviewedBy,
            @RequestParam(required = false) String reviewComment) {
        log.debug("PO attesting evidence {} for field {} by {}", evidenceId, profileFieldId, reviewedBy);
        EvidenceFieldLinkResponse response = evidenceAttestationService.attestEvidenceFieldLink(
                evidenceId, profileFieldId, reviewedBy,
                reviewComment != null ? reviewComment : "Attested by Product Owner", "po-approval");
        log.info("Evidence {} approved by PO {} for field {}", evidenceId, reviewedBy, profileFieldId);
        return ResponseEntity.ok(response);
    }

    /**
     * Approve or reject evidence (SME review workflow)
     * POST /api/evidence/{evidenceId}/review
     *
     * This endpoint is used by SMEs to approve or reject evidence submitted for
     * high-criticality fields. Approval/rejection triggers automatic risk recalculation
     * with evidence status multipliers applied to priority scores.
     */
    @PostMapping("/evidence/{evidenceId}/review")
    public ResponseEntity<EvidenceFieldLinkResponse> reviewEvidence(
            @PathVariable String evidenceId,
            @RequestParam String profileFieldId,
            @RequestBody ReviewEvidenceRequest request) {

        log.info("Reviewing evidence {} for field {} by {}: action={}",
                evidenceId, profileFieldId, request.reviewerId(), request.action());

        // Validate request
        if (!request.isValid()) {
            log.warn("Invalid review request: action={}, reviewerId={}",
                    request.action(), request.reviewerId());
            return ResponseEntity.badRequest().build();
        }

        // Convert action to boolean (approve = true, reject = false)
        boolean approved = request.isApproval();

        // Review the evidence field link (changes status and triggers risk recalculation)
        EvidenceFieldLinkResponse response = evidenceService.reviewEvidenceFieldLink(
                evidenceId,
                profileFieldId,
                request.reviewerId(),
                request.reviewComment() != null ? request.reviewComment() : "",
                approved);

        log.info("Evidence {} {} by {} for field {}",
                evidenceId,
                approved ? "APPROVED" : "REJECTED",
                request.reviewerId(),
                profileFieldId);

        return ResponseEntity.ok(response);
    }

    /**
     * Get evidence by KPI state: COMPLIANT
     * GET /api/evidence/by-state/compliant?appId=APM100001&page=0&size=10
     */
    @GetMapping("/evidence/by-state/compliant")
    public ResponseEntity<PageResponse<KpiEvidenceSummary>> getCompliantEvidence(
            @RequestParam(required = false) String appId,
            @RequestParam(required = false) String criticality,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String fieldKey,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.debug("Getting compliant evidence for appId: {}, criticality: {}, domain: {}, fieldKey: {}, search: {}, page: {}, size: {}",
                appId, criticality, domain, fieldKey, search, page, size);
        PageResponse<KpiEvidenceSummary> evidence =
                evidenceService.getCompliantEvidence(appId, criticality, domain, fieldKey, search, page, size);
        log.debug("Found {} compliant evidence items (page {} of {})", evidence.items().size(), evidence.page(), Math.ceil((double) evidence.total() / evidence.pageSize()));
        return ResponseEntity.ok(evidence);
    }

    /**
     * Get evidence by KPI state: PENDING REVIEW
     * GET /api/evidence/by-state/pending-review?appId=APM100001&page=1&size=10
     */
    @GetMapping("/evidence/by-state/pending-review")
    public ResponseEntity<PageResponse<KpiEvidenceSummary>> getPendingReviewEvidence(
            @RequestParam(required = false) String appId,
            @RequestParam(required = false) String criticality,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String fieldKey,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.debug("Getting pending review evidence for appId: {}, criticality: {}, domain: {}, fieldKey: {}, search: {}, page: {}, size: {}",
                appId, criticality, domain, fieldKey, search, page, size);
        PageResponse<KpiEvidenceSummary> evidence =
                evidenceService.getPendingReviewEvidence(appId, criticality, domain, fieldKey, search, page, size);
        log.debug("Found {} pending review evidence items (page {} of {})", evidence.items().size(), evidence.page(), Math.ceil((double) evidence.total() / evidence.pageSize()));
        return ResponseEntity.ok(evidence);
    }

    /**
     * Get profile fields by KPI state: MISSING EVIDENCE
     * GET /api/evidence/by-state/missing-evidence?appId=APM100001&page=1&size=10
     */
    @GetMapping("/evidence/by-state/missing-evidence")
    public ResponseEntity<PageResponse<Map<String, Object>>> getMissingEvidenceFields(
            @RequestParam(required = false) String appId,
            @RequestParam(required = false) String criticality,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String fieldKey,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.debug("Getting missing evidence fields for appId: {}, criticality: {}, domain: {}, fieldKey: {}, search: {}, page: {}, size: {}",
                appId, criticality, domain, fieldKey, search, page, size);
        PageResponse<Map<String, Object>> fields =
                evidenceService.getMissingEvidenceFields(appId, criticality, domain, fieldKey, search, page, size);
        log.debug("Found {} profile fields missing evidence (page {} of {})", fields.items().size(), fields.page(), Math.ceil((double) fields.total() / fields.pageSize()));
        return ResponseEntity.ok(fields);
    }

    /**
     * Get risks by KPI state: RISK BLOCKED
     * GET /api/evidence/by-state/risk-blocked?appId=APM100001&page=1&size=10
     */
    @GetMapping("/evidence/by-state/risk-blocked")
    public ResponseEntity<PageResponse<RiskBlockedItem>> getRiskBlockedItems(
            @RequestParam(required = false) String appId,
            @RequestParam(required = false) String criticality,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String fieldKey,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.debug("Getting risk blocked items for appId: {}, criticality: {}, domain: {}, fieldKey: {}, search: {}, page: {}, size: {}",
                appId, criticality, domain, fieldKey, search, page, size);
        PageResponse<RiskBlockedItem> risks =
                evidenceService.getRiskBlockedItems(appId, criticality, domain, fieldKey, search, page, size);
        log.debug("Found {} risk blocked items (page {} of {})", risks.items().size(), risks.page(), Math.ceil((double) risks.total() / risks.pageSize()));
        return ResponseEntity.ok(risks);
    }

    /**
     * Get evidence by multiple KPI states in a single call
     * GET /api/evidence/by-state?states=compliant,pending-review&appId=APM100001&page=1&size=10
     */
    @GetMapping("/evidence/by-state")
    public ResponseEntity<Map<String, Object>> getEvidenceByStates(
            @RequestParam(required = false) String appId,
            @RequestParam(required = false) String criticality,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String fieldKey,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "compliant,pending-review,missing-evidence,risk-blocked") String states,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.debug("Getting evidence by states: {} for appId: {}, criticality: {}, domain: {}, fieldKey: {}, search: {}, page: {}, size: {}",
                states, appId, criticality, domain, fieldKey, search, page, size);
        Map<String, Object> result = new HashMap<>();
        String[] stateArray = states.split(",");

            for (String state : stateArray) {
                state = state.trim();
                switch (state) {
                case "compliant":
        result.put("compliant", evidenceService.getCompliantEvidence(appId, criticality, domain, fieldKey, search, page, size));
            break;
                case "pending-review":
        result.put("pendingReview", evidenceService.getPendingReviewEvidence(appId, criticality, domain, fieldKey, search, page, size));
            break;
                case "missing-evidence":
        result.put("missingEvidence", evidenceService.getMissingEvidenceFields(appId, criticality, domain, fieldKey, search, page, size));
            break;
                case "risk-blocked":
        result.put("riskBlocked", evidenceService.getRiskBlockedItems(appId, criticality, domain, fieldKey, search, page, size));
            break;
                default:
        log.warn("Unknown state requested: {}", state);
                }
            }

        log.debug("Retrieved evidence for {} states", result.size());
        return ResponseEntity.ok(result);
    }
}