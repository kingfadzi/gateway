package com.example.gateway.risk.controller;

import com.example.gateway.risk.dto.*;
import com.example.gateway.risk.model.*;
import com.example.gateway.risk.repository.RiskItemRepository;
import com.example.gateway.risk.repository.RiskCommentRepository;
import com.example.gateway.risk.service.DomainRiskAggregationService;
import com.example.gateway.risk.service.RiskAssignmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Backward compatibility controller for legacy /api/risks/* endpoints.
 * Proxies requests to the new /api/v1/risk-items/* endpoints.
 *
 * This controller provides a transition period for frontend migration.
 * Can be removed once frontend is fully migrated to new endpoints.
 */
@RestController
@RequestMapping("/api/risks")
public class RiskLegacyCompatibilityController {

    private static final Logger log = LoggerFactory.getLogger(RiskLegacyCompatibilityController.class);

    private final RiskItemRepository riskItemRepository;
    private final RiskCommentRepository riskCommentRepository;
    private final DomainRiskAggregationService aggregationService;
    private final RiskAssignmentService assignmentService;

    public RiskLegacyCompatibilityController(
            RiskItemRepository riskItemRepository,
            RiskCommentRepository riskCommentRepository,
            DomainRiskAggregationService aggregationService,
            RiskAssignmentService assignmentService) {
        this.riskItemRepository = riskItemRepository;
        this.riskCommentRepository = riskCommentRepository;
        this.aggregationService = aggregationService;
        this.assignmentService = assignmentService;
    }

    /**
     * Legacy SME review endpoint - proxies to new RiskItemController.
     *
     * PUT /api/risks/{riskId}/sme-review
     *
     * ⚠️ DEPRECATED: Use PUT /api/v1/risk-items/{riskItemId}/sme-review instead
     */
    @PutMapping("/{riskId}/sme-review")
    public ResponseEntity<SmeReviewResponse> smeReview(
            @PathVariable String riskId,
            @RequestBody SmeReviewRequest request) {

        log.warn("Legacy endpoint called: PUT /api/risks/{}/sme-review - Frontend should migrate to /api/v1/risk-items/{}/sme-review",
                 riskId, riskId);

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
