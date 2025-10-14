package com.example.gateway.risk.dto;

/**
 * Risk breakdown by priority level.
 * Counts OPEN and IN_PROGRESS risk items only.
 */
public record RiskBreakdown(
    int critical,
    int high,
    int medium,
    int low
) {
    public static RiskBreakdown empty() {
        return new RiskBreakdown(0, 0, 0, 0);
    }
}
