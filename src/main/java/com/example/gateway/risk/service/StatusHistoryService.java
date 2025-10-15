package com.example.gateway.risk.service;

import com.example.gateway.risk.model.RiskItemStatus;
import com.example.gateway.risk.model.RiskItemStatusHistory;
import com.example.gateway.risk.repository.RiskItemStatusHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for tracking and managing risk item status history.
 * Automatically logs all status transitions with context and actor information.
 */
@Service
public class StatusHistoryService {

    private static final Logger log = LoggerFactory.getLogger(StatusHistoryService.class);

    private final RiskItemStatusHistoryRepository historyRepository;

    public StatusHistoryService(RiskItemStatusHistoryRepository historyRepository) {
        this.historyRepository = historyRepository;
    }

    /**
     * Log a status change with full context.
     *
     * @param riskItemId Risk item ID
     * @param fromStatus Previous status (null for initial creation)
     * @param toStatus New status
     * @param resolution Resolution type (e.g., SME_APPROVED, SME_REJECTED)
     * @param resolutionComment Explanation/notes
     * @param changedBy User who made the change
     * @param actorRole Role of user: SME, PO, SYSTEM, ADMIN
     * @return Saved history record
     */
    @Transactional
    public RiskItemStatusHistory logStatusChange(
            String riskItemId,
            RiskItemStatus fromStatus,
            RiskItemStatus toStatus,
            String resolution,
            String resolutionComment,
            String changedBy,
            String actorRole) {

        return logStatusChangeWithContext(
                riskItemId,
                fromStatus,
                toStatus,
                resolution,
                resolutionComment,
                changedBy,
                actorRole,
                null,  // mitigationPlan
                null,  // reassignedTo
                null   // metadata
        );
    }

    /**
     * Log a status change with full context including mitigation plan and metadata.
     *
     * @param riskItemId Risk item ID
     * @param fromStatus Previous status (null for initial creation)
     * @param toStatus New status
     * @param resolution Resolution type
     * @param resolutionComment Explanation/notes
     * @param changedBy User who made the change
     * @param actorRole Role of user: SME, PO, SYSTEM, ADMIN
     * @param mitigationPlan Mitigation plan (for SME_APPROVED_WITH_MITIGATION)
     * @param reassignedTo New assignee (for reassignments)
     * @param metadata Additional context (escalation details, etc.)
     * @return Saved history record
     */
    @Transactional
    public RiskItemStatusHistory logStatusChangeWithContext(
            String riskItemId,
            RiskItemStatus fromStatus,
            RiskItemStatus toStatus,
            String resolution,
            String resolutionComment,
            String changedBy,
            String actorRole,
            String mitigationPlan,
            String reassignedTo,
            Map<String, Object> metadata) {

        RiskItemStatusHistory history = new RiskItemStatusHistory();
        history.setHistoryId(UUID.randomUUID().toString());
        history.setRiskItemId(riskItemId);
        history.setFromStatus(fromStatus != null ? fromStatus.name() : null);
        history.setToStatus(toStatus.name());
        history.setResolution(resolution);
        history.setResolutionComment(resolutionComment);
        history.setChangedBy(changedBy);
        history.setActorRole(actorRole);
        history.setMitigationPlan(mitigationPlan);
        history.setReassignedTo(reassignedTo);
        history.setChangedAt(OffsetDateTime.now());
        history.setMetadata(metadata);

        RiskItemStatusHistory saved = historyRepository.save(history);

        log.info("Status change logged: risk_item={}, from={}, to={}, resolution={}, by={} ({})",
                riskItemId,
                fromStatus != null ? fromStatus.name() : "null",
                toStatus.name(),
                resolution,
                changedBy,
                actorRole);

        return saved;
    }

    /**
     * Get complete status history for a risk item.
     *
     * @param riskItemId Risk item ID
     * @return List of status changes ordered chronologically
     */
    public List<RiskItemStatusHistory> getHistory(String riskItemId) {
        return historyRepository.findByRiskItemIdOrderByChangedAtAsc(riskItemId);
    }

    /**
     * Get recent status history for a risk item.
     *
     * @param riskItemId Risk item ID
     * @param limit Maximum number of records
     * @return List of recent status changes
     */
    public List<RiskItemStatusHistory> getRecentHistory(String riskItemId, int limit) {
        return historyRepository.findRecentByRiskItemId(riskItemId, limit);
    }

    /**
     * Get most recent status change for a risk item.
     *
     * @param riskItemId Risk item ID
     * @return Most recent status change, or null if none exists
     */
    public RiskItemStatusHistory getMostRecentChange(String riskItemId) {
        return historyRepository.findMostRecentByRiskItemId(riskItemId);
    }

    /**
     * Check if a risk item has ever been in a specific status.
     *
     * @param riskItemId Risk item ID
     * @param status Status to check
     * @return true if risk item was ever in this status
     */
    public boolean wasEverInStatus(String riskItemId, RiskItemStatus status) {
        return historyRepository.existsByRiskItemIdAndToStatus(riskItemId, status.name());
    }

    /**
     * Count total status transitions for a risk item.
     *
     * @param riskItemId Risk item ID
     * @return Number of status changes
     */
    public long countTransitions(String riskItemId) {
        return historyRepository.countByRiskItemId(riskItemId);
    }

    // =====================
    // Helper Methods for Common Scenarios
    // =====================

    /**
     * Log SME approval (simple).
     */
    public RiskItemStatusHistory logSmeApproval(
            String riskItemId,
            RiskItemStatus fromStatus,
            String smeId,
            String comments) {

        return logStatusChange(
                riskItemId,
                fromStatus,
                RiskItemStatus.SME_APPROVED,
                "SME_APPROVED",
                comments,
                smeId,
                "SME"
        );
    }

    /**
     * Log SME approval with mitigation.
     */
    public RiskItemStatusHistory logSmeApprovalWithMitigation(
            String riskItemId,
            RiskItemStatus fromStatus,
            String smeId,
            String comments,
            String mitigationPlan) {

        return logStatusChangeWithContext(
                riskItemId,
                fromStatus,
                RiskItemStatus.SME_APPROVED,
                "SME_APPROVED_WITH_MITIGATION",
                comments,
                smeId,
                "SME",
                mitigationPlan,
                null,
                null
        );
    }

    /**
     * Log SME rejection.
     */
    public RiskItemStatusHistory logSmeRejection(
            String riskItemId,
            RiskItemStatus fromStatus,
            String smeId,
            String comments) {

        return logStatusChange(
                riskItemId,
                fromStatus,
                RiskItemStatus.AWAITING_REMEDIATION,
                "SME_REJECTED",
                comments,
                smeId,
                "SME"
        );
    }

    /**
     * Log SME escalation.
     */
    public RiskItemStatusHistory logSmeEscalation(
            String riskItemId,
            RiskItemStatus fromStatus,
            String smeId,
            String comments) {

        return logStatusChange(
                riskItemId,
                fromStatus,
                RiskItemStatus.ESCALATED,
                "SME_ESCALATED",
                comments,
                smeId,
                "SME"
        );
    }

    /**
     * Log PO self-attestation.
     */
    public RiskItemStatusHistory logPoSelfAttestation(
            String riskItemId,
            String poId,
            String attestationStatement) {

        return logStatusChange(
                riskItemId,
                RiskItemStatus.PENDING_REVIEW,
                RiskItemStatus.SELF_ATTESTED,
                "PO_SELF_ATTESTED",
                attestationStatement,
                poId,
                "PO"
        );
    }

    /**
     * Log reassignment.
     */
    public RiskItemStatusHistory logReassignment(
            String riskItemId,
            RiskItemStatus fromStatus,
            String assignedBy,
            String assignedTo,
            String reason) {

        return logStatusChangeWithContext(
                riskItemId,
                fromStatus,
                RiskItemStatus.PENDING_REVIEW,
                "REASSIGNED_TO_SME",
                reason,
                assignedBy,
                "SME",
                null,
                assignedTo,
                null
        );
    }

    /**
     * Log system auto-creation.
     */
    public RiskItemStatusHistory logSystemCreation(String riskItemId, String triggerReason) {
        return logStatusChange(
                riskItemId,
                null,  // No previous status
                RiskItemStatus.PENDING_REVIEW,
                "SYSTEM_AUTO_CREATED",
                triggerReason,
                "SYSTEM",
                "SYSTEM"
        );
    }
}
