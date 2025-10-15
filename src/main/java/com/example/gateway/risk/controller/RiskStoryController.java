package com.example.gateway.risk.controller;

import com.example.gateway.application.dto.PageResponse;
import com.example.gateway.risk.dto.*;
import com.example.gateway.risk.model.*;
import com.example.gateway.risk.repository.RiskItemRepository;
import com.example.gateway.risk.repository.RiskCommentRepository;
import com.example.gateway.risk.service.DomainRiskAggregationService;
import com.example.gateway.risk.service.RiskAssignmentService;
import com.example.gateway.risk.service.RiskStoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * DEPRECATED: This controller is deprecated as of V7 migration.
 * All risk_story data has been migrated to the new risk_item architecture.
 *
 * Use the new endpoints instead:
 * - GET /api/v1/risk-items/app/{appId} - Get risks for an application
 * - GET /api/v1/domain-risks/app/{appId} - Get domain-level risk aggregations
 * - POST /api/v1/risk-items - Create manual risk items
 *
 * Migration completed: 383 risk_story records migrated to risk_item.
 * Mapping table available: risk_story_to_item_mapping
 */
@Deprecated(since = "V7", forRemoval = true)
@RestController
@RequestMapping("/api")
public class RiskStoryController {

    private static final Logger log = LoggerFactory.getLogger(RiskStoryController.class);

    private final RiskStoryService riskStoryService;
    private final RiskItemRepository riskItemRepository;
    private final RiskCommentRepository riskCommentRepository;
    private final DomainRiskAggregationService aggregationService;
    private final RiskAssignmentService assignmentService;

    public RiskStoryController(
            RiskStoryService riskStoryService,
            RiskItemRepository riskItemRepository,
            RiskCommentRepository riskCommentRepository,
            DomainRiskAggregationService aggregationService,
            RiskAssignmentService assignmentService) {
        this.riskStoryService = riskStoryService;
        this.riskItemRepository = riskItemRepository;
        this.riskCommentRepository = riskCommentRepository;
        this.aggregationService = aggregationService;
        this.assignmentService = assignmentService;
    }

    @Deprecated(since = "V7", forRemoval = true)
    @PostMapping("/apps/{appId}/fields/{fieldKey}/risks")
    public ResponseEntity<Map<String, Object>> createRiskStory(
            @PathVariable String appId,
            @PathVariable String fieldKey,
            @RequestBody CreateRiskStoryRequest request) {
        return ResponseEntity.status(HttpStatus.GONE).body(Map.of(
            "error", "ENDPOINT_DEPRECATED",
            "message", "This endpoint has been deprecated. Risk stories have been migrated to risk items.",
            "migration_date", "V7",
            "replacement_endpoint", "POST /api/v1/risk-items",
            "documentation", "See DomainRiskController and RiskItemController for new API",
            "note", "All 383 risk_story records have been migrated to risk_item. Check risk_story_to_item_mapping for ID mappings."
        ));
    }

    @Deprecated(since = "V7", forRemoval = true)
    @PostMapping("/risks/{riskId}/evidence")
    public ResponseEntity<Map<String, Object>> attachEvidence(
            @PathVariable String riskId,
            @RequestBody AttachEvidenceRequest request) {
        return ResponseEntity.status(HttpStatus.GONE).body(Map.of(
            "error", "ENDPOINT_DEPRECATED",
            "message", "This endpoint has been deprecated. Risk stories have been migrated to risk items.",
            "migration_date", "V7",
            "replacement_endpoint", "Evidence is now linked directly when creating risk items via POST /api/v1/risk-items",
            "documentation", "See RiskItemController for new API",
            "note", "All 383 risk_story records have been migrated to risk_item. Check risk_story_to_item_mapping for ID mappings."
        ));
    }

    @Deprecated(since = "V7", forRemoval = true)
    @DeleteMapping("/risks/{riskId}/evidence/{evidenceId}")
    public ResponseEntity<Map<String, Object>> detachEvidence(
            @PathVariable String riskId,
            @PathVariable String evidenceId) {
        return ResponseEntity.status(HttpStatus.GONE).body(Map.of(
            "error", "ENDPOINT_DEPRECATED",
            "message", "This endpoint has been deprecated. Risk stories have been migrated to risk items.",
            "migration_date", "V7",
            "replacement_endpoint", "Evidence management is now handled through risk_item relationships",
            "documentation", "See RiskItemController for new API",
            "note", "All 383 risk_story records have been migrated to risk_item. Check risk_story_to_item_mapping for ID mappings."
        ));
    }

    @Deprecated(since = "V7", forRemoval = true)
    @GetMapping("/apps/{appId}/risks")
    public ResponseEntity<Map<String, Object>> getAppRisks(
            @PathVariable String appId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ResponseEntity.status(HttpStatus.GONE).body(Map.of(
            "error", "ENDPOINT_DEPRECATED",
            "message", "This endpoint has been deprecated. Risk stories have been migrated to risk items.",
            "migration_date", "V7",
            "replacement_endpoint", "GET /api/v1/risk-items/app/" + appId,
            "documentation", "See RiskItemController for new API",
            "note", "All 383 risk_story records have been migrated to risk_item. Check risk_story_to_item_mapping for ID mappings."
        ));
    }

    @Deprecated(since = "V7", forRemoval = true)
    @GetMapping("/risks/{riskId}")
    public ResponseEntity<Map<String, Object>> getRiskById(@PathVariable String riskId) {
        return ResponseEntity.status(HttpStatus.GONE).body(Map.of(
            "error", "ENDPOINT_DEPRECATED",
            "message", "This endpoint has been deprecated. Risk stories have been migrated to risk items.",
            "migration_date", "V7",
            "replacement_endpoint", "GET /api/v1/risk-items/{riskItemId}",
            "documentation", "See RiskItemController for new API",
            "note", "All 383 risk_story records have been migrated to risk_item. Check risk_story_to_item_mapping for old_risk_id → new_risk_item_id mappings.",
            "lookup_query", "SELECT new_risk_item_id FROM risk_story_to_item_mapping WHERE old_risk_id = '" + riskId + "'"
        ));
    }

    @Deprecated(since = "V7", forRemoval = true)
    @GetMapping("/apps/{appId}/fields/{fieldKey}/risks")
    public ResponseEntity<Map<String, Object>> getRisksByFieldKey(
            @PathVariable String appId,
            @PathVariable String fieldKey) {
        return ResponseEntity.status(HttpStatus.GONE).body(Map.of(
            "error", "ENDPOINT_DEPRECATED",
            "message", "This endpoint has been deprecated. Risk stories have been migrated to risk items.",
            "migration_date", "V7",
            "replacement_endpoint", "GET /api/v1/risk-items/field/" + fieldKey,
            "documentation", "See RiskItemController for new API",
            "note", "All 383 risk_story records have been migrated to risk_item. Filter by appId on the client side if needed."
        ));
    }

    @Deprecated(since = "V7", forRemoval = true)
    @GetMapping("/profile-fields/{profileFieldId}/risks")
    public ResponseEntity<Map<String, Object>> getRisksByProfileFieldId(@PathVariable String profileFieldId) {
        return ResponseEntity.status(HttpStatus.GONE).body(Map.of(
            "error", "ENDPOINT_DEPRECATED",
            "message", "This endpoint has been deprecated. Risk stories have been migrated to risk items.",
            "migration_date", "V7",
            "replacement_endpoint", "GET /api/v1/risk-items/app/{appId} (filter by profileFieldId on client side)",
            "documentation", "See RiskItemController for new API",
            "note", "All 383 risk_story records have been migrated to risk_item. Profile field filtering available via app-based queries."
        ));
    }

    @Deprecated(since = "V7", forRemoval = true)
    @GetMapping("/risks/search")
    public ResponseEntity<Map<String, Object>> searchRisks(
            @RequestParam(required = false) String appId,
            @RequestParam(required = false) String assignedSme,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String derivedFrom,
            @RequestParam(required = false) String fieldKey,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String creationType,
            @RequestParam(required = false) String triggeringEvidenceId,
            @RequestParam(defaultValue = "assignedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortOrder,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {

        return ResponseEntity.status(HttpStatus.GONE).body(Map.of(
            "error", "ENDPOINT_DEPRECATED",
            "message", "This endpoint has been deprecated. Risk stories have been migrated to risk items.",
            "migration_date", "V7",
            "replacement_endpoints", Map.of(
                "by_app", "GET /api/v1/risk-items/app/{appId}",
                "by_field", "GET /api/v1/risk-items/field/{fieldKey}",
                "by_evidence", "GET /api/v1/risk-items/evidence/{evidenceId}",
                "by_status", "GET /api/v1/risk-items/app/{appId}/status/{status}",
                "domain_view", "GET /api/v1/domain-risks/arb/{arbName}"
            ),
            "documentation", "See RiskItemController and DomainRiskController for new API",
            "note", "All 383 risk_story records have been migrated to risk_item. The assignedSme field is now replaced by ARB assignment at the domain level."
        ));
    }

    /**
     * SME review endpoint for risk items.
     * Provides backward compatibility for frontend calling PUT /api/risks/{riskId}/sme-review
     *
     * PUT /api/risks/{riskId}/sme-review
     *
     * Supported actions:
     * - approve: Marks risk as WAIVED (SME accepts the risk)
     * - approve_with_mitigation: Marks risk as WAIVED with mitigation plan
     * - reject: Keeps risk OPEN (SME requires remediation)
     * - request_info: Marks risk as IN_PROGRESS (needs more information)
     * - assign_other: Reassign to another SME
     * - escalate: Escalate the risk (special handling)
     */
    @PutMapping("/risks/{riskId}/sme-review")
    public ResponseEntity<SmeReviewResponse> smeReview(
            @PathVariable String riskId,
            @RequestBody SmeReviewRequest request) {

        log.info("PUT /api/risks/{}/sme-review - action: {}, smeId: {}",
                riskId, request.action(), request.smeId());

        // Validate request
        if (!request.isValid()) {
            String validationError = getValidationError(request);
            log.warn("Invalid SME review request for riskId {}: {} - Request: {}",
                    riskId, validationError, request);
            return ResponseEntity.badRequest()
                    .body(new SmeReviewResponse(
                            riskId,
                            "VALIDATION_ERROR",
                            request.smeId(),
                            OffsetDateTime.now(),
                            validationError
                    ));
        }

        // Verify risk item exists
        if (!riskItemRepository.existsById(riskId)) {
            log.warn("Risk item not found: {}", riskId);
            return ResponseEntity.notFound().build();
        }

        // Add SME comment
        String commentText = buildCommentText(request);
        RiskComment comment = new RiskComment();
        comment.setCommentId("comment_" + UUID.randomUUID());
        comment.setRiskItemId(riskId);
        comment.setCommentType(RiskCommentType.REVIEW);
        comment.setCommentText(commentText);
        comment.setCommentedBy(request.smeId());
        comment.setIsInternal(false);
        comment.setCommentedAt(OffsetDateTime.now());
        riskCommentRepository.save(comment);

        // Handle different actions
        String status;
        if (request.isApprove() || request.isApproveWithMitigation()) {
            // Approve: SME accepts the risk
            String resolutionComment = request.isApproveWithMitigation()
                    ? "SME approved with mitigation plan: " + request.mitigationPlan()
                    : "SME approved by " + request.smeId();
            if (request.comments() != null) {
                resolutionComment += " - " + request.comments();
            }

            aggregationService.updateRiskItemStatus(
                    riskId,
                    RiskItemStatus.SME_APPROVED,
                    "SME_APPROVED",
                    resolutionComment,
                    request.smeId(),
                    "SME"
            );
            status = "SME_APPROVED";
            log.info("SME approved risk item: {} (mitigation: {})", riskId, request.isApproveWithMitigation());

        } else if (request.isReject()) {
            // Reject: SME requires remediation
            aggregationService.updateRiskItemStatus(
                    riskId,
                    RiskItemStatus.AWAITING_REMEDIATION,
                    "SME_REJECTED",
                    "SME rejected by " + request.smeId() + ": " +
                            (request.comments() != null ? request.comments() : "Requires remediation"),
                    request.smeId(),
                    "SME"
            );
            status = "SME_REJECTED";
            log.info("SME rejected risk item: {}", riskId);

        } else if (request.isRequestInfo()) {
            // Request info: Send back to PO to provide additional information
            aggregationService.updateRiskItemStatus(
                    riskId,
                    RiskItemStatus.AWAITING_REMEDIATION,
                    "INFO_REQUESTED",
                    "SME " + request.smeId() + " requested more information: " +
                            (request.comments() != null ? request.comments() : "Additional information needed"),
                    request.smeId(),
                    "SME"
            );
            status = "INFO_REQUESTED";
            log.info("SME requested info for risk item: {}", riskId);

        } else if (request.isAssignOther()) {
            // Reassign to another SME - use assignment service
            try {
                AssignRiskItemRequest assignRequest = new AssignRiskItemRequest(
                        request.assignToSme(),
                        "Reassigned by " + request.smeId() + ": " +
                                (request.comments() != null ? request.comments() : "Reassignment")
                );
                assignmentService.assignToUser(riskId, assignRequest, request.smeId());
                status = "REASSIGNED";
                log.info("SME reassigned risk item: {} to {}", riskId, request.assignToSme());
            } catch (Exception e) {
                log.error("Failed to reassign risk item: {}", riskId, e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

        } else if (request.isEscalate()) {
            // Escalate: Mark as ESCALATED (active state, awaiting resolution)
            aggregationService.updateRiskItemStatus(
                    riskId,
                    RiskItemStatus.ESCALATED,
                    "ESCALATED",
                    "Risk escalated by " + request.smeId() + ": " +
                            (request.comments() != null ? request.comments() : "Escalated for review"),
                    request.smeId(),
                    "SME"
            );
            status = "ESCALATED";
            log.info("SME escalated risk item: {}", riskId);

        } else {
            log.warn("Unhandled action: {}", request.action());
            return ResponseEntity.badRequest().build();
        }

        // Build response
        OffsetDateTime reviewedAt = OffsetDateTime.now();
        SmeReviewResponse response = new SmeReviewResponse(
                riskId,
                status,
                request.smeId(),
                reviewedAt
        );

        log.info("SME review completed for risk item: {} - status: {}", riskId, status);
        return ResponseEntity.ok(response);
    }

    /**
     * Build comment text based on action and request data.
     */
    private String buildCommentText(SmeReviewRequest request) {
        StringBuilder sb = new StringBuilder();

        if (request.isApprove()) {
            sb.append("✅ SME Approved");
        } else if (request.isApproveWithMitigation()) {
            sb.append("✅ SME Approved with Mitigation\n\n");
            sb.append("**Mitigation Plan:**\n").append(request.mitigationPlan());
        } else if (request.isReject()) {
            sb.append("❌ SME Rejected");
        } else if (request.isRequestInfo()) {
            sb.append("ℹ️ SME Requested More Information");
        } else if (request.isAssignOther()) {
            sb.append("🔄 Reassigned to ").append(request.assignToSme());
        } else if (request.isEscalate()) {
            sb.append("⬆️ SME Escalated");
        }

        if (request.comments() != null && !request.comments().isBlank()) {
            sb.append("\n\n**Comments:**\n").append(request.comments());
        }

        return sb.toString();
    }

    /**
     * Get detailed validation error message for logging and response.
     */
    private String getValidationError(SmeReviewRequest request) {
        if (request.action() == null || request.action().isBlank()) {
            return "Missing required field: 'action' (must be one of: approve, approve_with_mitigation, reject, request_info, assign_other, escalate)";
        }

        if (request.smeId() == null || request.smeId().isBlank()) {
            return "Missing required field: 'smeId'";
        }

        String lowerAction = request.action().toLowerCase();
        boolean validAction = lowerAction.equals("approve")
                || lowerAction.equals("approve_with_mitigation")
                || lowerAction.equals("reject")
                || lowerAction.equals("request_info")
                || lowerAction.equals("assign_other")
                || lowerAction.equals("escalate");

        if (!validAction) {
            return "Invalid action: '" + request.action() + "' (must be one of: approve, approve_with_mitigation, reject, request_info, assign_other, escalate)";
        }

        if (lowerAction.equals("approve_with_mitigation") && (request.mitigationPlan() == null || request.mitigationPlan().isBlank())) {
            return "Missing required field: 'mitigationPlan' (required for action 'approve_with_mitigation')";
        }

        if (lowerAction.equals("assign_other") && (request.assignToSme() == null || request.assignToSme().isBlank())) {
            return "Missing required field: 'assignToSme' (required for action 'assign_other')";
        }

        return "Invalid request";
    }
}
