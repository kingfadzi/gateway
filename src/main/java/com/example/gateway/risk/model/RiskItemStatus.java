package com.example.gateway.risk.model;

/**
 * Status of an individual risk item within a domain risk.
 *
 * Two mutually exclusive workflows:
 * 1. Self-Attestation Flow: PENDING_REVIEW → SELF_ATTESTED (terminal)
 * 2. SME Assessment Flow: PENDING_REVIEW → UNDER_SME_REVIEW → (various outcomes)
 *
 * Active States (counted as "open" in aggregations):
 * - PENDING_REVIEW, UNDER_SME_REVIEW, AWAITING_REMEDIATION,
 *   IN_REMEDIATION, PENDING_APPROVAL, ESCALATED
 *
 * Terminal States (not counted as open):
 * - SME_APPROVED, SELF_ATTESTED, REMEDIATED, CLOSED
 */
public enum RiskItemStatus {

    // ==================== Active States ====================

    /**
     * Initial state - awaiting triage/SME assignment.
     * Can transition to:
     * - UNDER_SME_REVIEW (SME self-assigns)
     * - SELF_ATTESTED (PO self-attests - terminal)
     */
    PENDING_REVIEW,

    /**
     * SME is actively reviewing the risk.
     * Can transition to:
     * - SME_APPROVED (SME approves - terminal)
     * - AWAITING_REMEDIATION (SME rejects or requests info)
     * - ESCALATED (SME escalates)
     * - PENDING_REVIEW (SME reassigns)
     */
    UNDER_SME_REVIEW,

    /**
     * SME rejected or requested more info - PO needs to fix/provide evidence.
     * Can transition to:
     * - PENDING_APPROVAL (PO submits evidence)
     * - IN_REMEDIATION (PO marks as in remediation)
     * - CLOSED (admin closes - terminal)
     */
    AWAITING_REMEDIATION,

    /**
     * PO is actively working on remediation.
     * Can transition to:
     * - PENDING_APPROVAL (PO submits evidence)
     * - CLOSED (admin closes - terminal)
     */
    IN_REMEDIATION,

    /**
     * Evidence submitted by PO, awaiting final SME approval.
     * Can transition to:
     * - REMEDIATED (SME approves evidence - terminal)
     * - AWAITING_REMEDIATION (SME rejects evidence)
     * - ESCALATED (SME escalates)
     */
    PENDING_APPROVAL,

    /**
     * Escalated to higher authority - awaiting escalation resolution.
     * Remains open until SME closes after escalation is resolved.
     * Can transition to:
     * - SME_APPROVED (escalation resolved favorably - terminal)
     * - CLOSED (closed after escalation - terminal)
     * - AWAITING_REMEDIATION (escalation requires PO action)
     */
    ESCALATED,

    // ==================== Terminal States ====================

    /**
     * SME accepted the risk (with or without mitigation).
     * Terminal state - counted as closed.
     * Resolution types:
     * - SME_APPROVED
     * - SME_APPROVED_WITH_MITIGATION
     * - SME_APPROVED_POST_ESCALATION
     */
    SME_APPROVED,

    /**
     * PO self-attested (no SME review required).
     * Separate flow from SME assessment - mutually exclusive.
     * Terminal state - counted as closed.
     * Resolution type: PO_SELF_ATTESTED
     */
    SELF_ATTESTED,

    /**
     * PO fixed the issue and SME approved the remediation.
     * Terminal state - counted as closed.
     * Resolution type: SME_APPROVED_REMEDIATION
     */
    REMEDIATED,

    /**
     * Administrative closure or post-escalation closure.
     * Terminal state - counted as closed.
     * Resolution types:
     * - ADMIN_CLOSED
     * - CLOSED_POST_ESCALATION
     */
    CLOSED;

    /**
     * Check if this status is terminal (not counted as open).
     */
    public boolean isTerminal() {
        return this == SME_APPROVED ||
               this == SELF_ATTESTED ||
               this == REMEDIATED ||
               this == CLOSED;
    }

    /**
     * Check if this status is active (counted as open).
     */
    public boolean isActive() {
        return !isTerminal();
    }
}
