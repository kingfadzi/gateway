package com.example.gateway.application.dto;

import com.example.gateway.risk.dto.RiskBreakdown;

/**
 * Risk metrics aggregation for an application.
 * Contains breakdowns and scores for risk items associated with an app.
 * Matches the structure of SME dashboard for consistency.
 */
public record RiskMetrics(
    RiskBreakdown totalRisks,    // Breakdown of all risk items (critical/high/medium/low)
    RiskBreakdown inProgress,    // Breakdown of risks in active statuses (same as totalRisks)
    Integer riskScore            // Calculated priority score (0-100 scale, MAX across domains)
) {
    /**
     * Create empty risk metrics (no risks).
     */
    public static RiskMetrics empty() {
        return new RiskMetrics(
            RiskBreakdown.empty(),
            RiskBreakdown.empty(),
            0
        );
    }
}
