package com.example.gateway.risk.model;

/**
 * Enum representing the type of risk item assignment action.
 * Used for audit trail and reporting.
 */
public enum AssignmentType {
    /**
     * User assigned the risk to themselves.
     */
    SELF_ASSIGN,

    /**
     * Another user (manager/admin) assigned the risk.
     */
    MANUAL_ASSIGN,

    /**
     * System automatically assigned the risk (e.g., round-robin, load balancing).
     */
    AUTO_ASSIGN,

    /**
     * Risk was unassigned and returned to the pool.
     */
    UNASSIGN
}
