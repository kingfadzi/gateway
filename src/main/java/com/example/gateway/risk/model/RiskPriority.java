package com.example.gateway.risk.model;

/**
 * Priority levels for risks, derived from registry rules and calculated scores.
 *
 * Score ranges:
 * - CRITICAL: 90-100
 * - HIGH: 70-89
 * - MEDIUM: 40-69
 * - LOW: 0-39
 */
public enum RiskPriority {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW;

    /**
     * Determine priority level from calculated score
     */
    public static RiskPriority fromScore(int score) {
        if (score >= 90) return CRITICAL;
        if (score >= 70) return HIGH;
        if (score >= 40) return MEDIUM;
        return LOW;
    }

    /**
     * Get base score for this priority level (used in calculations)
     */
    public int getBaseScore() {
        return switch (this) {
            case CRITICAL -> 40;
            case HIGH -> 30;
            case MEDIUM -> 20;
            case LOW -> 10;
        };
    }
}
