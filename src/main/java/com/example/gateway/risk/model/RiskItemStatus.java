package com.example.gateway.risk.model;

/**
 * Status of an individual risk item within a domain risk.
 *
 * Flow: OPEN → IN_PROGRESS → RESOLVED
 * Alternative: Any status → WAIVED or CLOSED
 */
public enum RiskItemStatus {
    /**
     * Risk item needs attention
     */
    OPEN,

    /**
     * Being actively worked on
     */
    IN_PROGRESS,

    /**
     * Evidence provided and accepted
     */
    RESOLVED,

    /**
     * ARB waived this specific item
     */
    WAIVED,

    /**
     * Closed without resolution
     */
    CLOSED
}
