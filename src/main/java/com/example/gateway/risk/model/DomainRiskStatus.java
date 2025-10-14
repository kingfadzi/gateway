package com.example.gateway.risk.model;

/**
 * Status of a domain-level risk aggregation.
 *
 * Flow: PENDING_ARB_REVIEW → UNDER_ARB_REVIEW → AWAITING_REMEDIATION → IN_PROGRESS → RESOLVED
 * Alternative: Any status → WAIVED or CLOSED
 */
public enum DomainRiskStatus {
    /**
     * Domain risk created, awaiting ARB assignment/review
     */
    PENDING_ARB_REVIEW,

    /**
     * ARB is currently reviewing the domain risk
     */
    UNDER_ARB_REVIEW,

    /**
     * ARB reviewed, awaiting remediation from product owners
     */
    AWAITING_REMEDIATION,

    /**
     * Remediation in progress
     */
    IN_PROGRESS,

    /**
     * All risk items resolved
     */
    RESOLVED,

    /**
     * ARB waived the entire domain risk
     */
    WAIVED,

    /**
     * Administratively closed without resolution
     */
    CLOSED
}
