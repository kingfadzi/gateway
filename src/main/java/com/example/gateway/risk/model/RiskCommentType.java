package com.example.gateway.risk.model;

/**
 * Type of risk comment for categorization.
 */
public enum RiskCommentType {
    /**
     * General comment or discussion
     */
    GENERAL,

    /**
     * Comment related to status change
     */
    STATUS_CHANGE,

    /**
     * ARB/SME review comment
     */
    REVIEW,

    /**
     * Resolution-related comment
     */
    RESOLUTION
}
