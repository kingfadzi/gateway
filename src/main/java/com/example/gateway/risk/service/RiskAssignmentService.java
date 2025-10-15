package com.example.gateway.risk.service;

import com.example.gateway.risk.dto.AssignRiskItemRequest;
import com.example.gateway.risk.dto.AssignmentResponse;
import com.example.gateway.risk.model.*;
import com.example.gateway.risk.repository.RiskItemRepository;
import com.example.gateway.risk.repository.RiskItemAssignmentHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Service for managing risk item assignments.
 * Handles self-assignment, manual assignment, and unassignment workflows.
 */
@Service
public class RiskAssignmentService {

    private static final Logger log = LoggerFactory.getLogger(RiskAssignmentService.class);

    private final RiskItemRepository riskItemRepository;
    private final RiskItemAssignmentHistoryRepository historyRepository;

    public RiskAssignmentService(
            RiskItemRepository riskItemRepository,
            RiskItemAssignmentHistoryRepository historyRepository) {
        this.riskItemRepository = riskItemRepository;
        this.historyRepository = historyRepository;
    }

    /**
     * Self-assign a risk item to current user.
     * Auto-transitions risk from OPEN to IN_PROGRESS.
     */
    @Transactional
    public AssignmentResponse selfAssign(String riskItemId, String currentUser) {
        log.info("Self-assigning risk {} to user {}", riskItemId, currentUser);

        RiskItem riskItem = riskItemRepository.findById(riskItemId)
                .orElseThrow(() -> new IllegalArgumentException("Risk item not found: " + riskItemId));

        // Validation: Can't self-assign if already assigned to someone else
        if (riskItem.getAssignedTo() != null && !riskItem.getAssignedTo().equals(currentUser)) {
            throw new IllegalStateException(
                String.format("Risk item %s is already assigned to %s. Use re-assign instead.",
                    riskItemId, riskItem.getAssignedTo())
            );
        }

        // Validation: Can't assign terminal state risks
        if (riskItem.getStatus().isTerminal()) {
            throw new IllegalStateException("Cannot assign closed/resolved risk items");
        }

        String previousAssignee = riskItem.getAssignedTo();

        // Assign
        riskItem.setAssignedTo(currentUser);
        riskItem.setAssignedBy(currentUser);
        riskItem.setAssignedAt(OffsetDateTime.now());

        // Auto-transition to UNDER_SME_REVIEW if still PENDING_REVIEW
        if (riskItem.getStatus() == RiskItemStatus.PENDING_REVIEW) {
            riskItem.setStatus(RiskItemStatus.UNDER_SME_REVIEW);
        }

        riskItemRepository.save(riskItem);

        // Record history
        recordAssignmentHistory(riskItem, previousAssignee, currentUser,
                                AssignmentType.SELF_ASSIGN, "Self-assigned by user");

        log.info("Risk {} self-assigned to {}", riskItemId, currentUser);

        return new AssignmentResponse(
            riskItemId,
            currentUser,
            currentUser,
            riskItem.getAssignedAt(),
            "SELF_ASSIGN",
            "Risk item successfully self-assigned"
        );
    }

    /**
     * Assign a risk item to another user.
     * Auto-transitions risk from OPEN to IN_PROGRESS.
     */
    @Transactional
    public AssignmentResponse assignToUser(String riskItemId, AssignRiskItemRequest request, String currentUser) {
        log.info("Assigning risk {} to user {} by {}", riskItemId, request.assignedTo(), currentUser);

        RiskItem riskItem = riskItemRepository.findById(riskItemId)
                .orElseThrow(() -> new IllegalArgumentException("Risk item not found: " + riskItemId));

        // Validation: Can't assign terminal state risks
        if (riskItem.getStatus().isTerminal()) {
            throw new IllegalStateException("Cannot assign closed/resolved risk items");
        }

        String previousAssignee = riskItem.getAssignedTo();

        // Assign
        riskItem.setAssignedTo(request.assignedTo());
        riskItem.setAssignedBy(currentUser);
        riskItem.setAssignedAt(OffsetDateTime.now());

        // Auto-transition to UNDER_SME_REVIEW if still PENDING_REVIEW
        if (riskItem.getStatus() == RiskItemStatus.PENDING_REVIEW) {
            riskItem.setStatus(RiskItemStatus.UNDER_SME_REVIEW);
        }

        riskItemRepository.save(riskItem);

        // Record history
        recordAssignmentHistory(riskItem, previousAssignee, currentUser,
                                AssignmentType.MANUAL_ASSIGN, request.reason());

        log.info("Risk {} assigned to {} by {}", riskItemId, request.assignedTo(), currentUser);

        return new AssignmentResponse(
            riskItemId,
            request.assignedTo(),
            currentUser,
            riskItem.getAssignedAt(),
            "MANUAL_ASSIGN",
            String.format("Risk item assigned to %s", request.assignedTo())
        );
    }

    /**
     * Unassign a risk item (return to pool).
     * Transitions IN_PROGRESS risks back to OPEN.
     */
    @Transactional
    public AssignmentResponse unassign(String riskItemId, String currentUser, String reason) {
        log.info("Unassigning risk {} by user {}", riskItemId, currentUser);

        RiskItem riskItem = riskItemRepository.findById(riskItemId)
                .orElseThrow(() -> new IllegalArgumentException("Risk item not found: " + riskItemId));

        String previousAssignee = riskItem.getAssignedTo();

        if (previousAssignee == null) {
            throw new IllegalStateException("Risk item is not assigned to anyone");
        }

        // Unassign
        riskItem.setAssignedTo(null);
        riskItem.setAssignedBy(null);
        riskItem.setAssignedAt(null);

        // Transition back to PENDING_REVIEW if it was UNDER_SME_REVIEW
        if (riskItem.getStatus() == RiskItemStatus.UNDER_SME_REVIEW) {
            riskItem.setStatus(RiskItemStatus.PENDING_REVIEW);
        }

        riskItemRepository.save(riskItem);

        // Record history
        recordAssignmentHistory(riskItem, previousAssignee, currentUser,
                                AssignmentType.UNASSIGN, reason);

        log.info("Risk {} unassigned by {}, was assigned to {}", riskItemId, currentUser, previousAssignee);

        return new AssignmentResponse(
            riskItemId,
            null,
            currentUser,
            OffsetDateTime.now(),
            "UNASSIGN",
            "Risk item returned to unassigned pool"
        );
    }

    /**
     * Record assignment history (audit trail).
     */
    private void recordAssignmentHistory(RiskItem riskItem, String previousAssignee,
                                         String currentUser, AssignmentType type, String reason) {
        RiskItemAssignmentHistory history = new RiskItemAssignmentHistory();
        history.setHistoryId("history_" + UUID.randomUUID());
        history.setRiskItemId(riskItem.getRiskItemId());
        history.setAssignedTo(riskItem.getAssignedTo());
        history.setAssignedFrom(previousAssignee);
        history.setAssignedBy(currentUser);
        history.setAssignmentType(type);
        history.setReason(reason);
        history.setAssignedAt(OffsetDateTime.now());

        historyRepository.save(history);
    }
}
