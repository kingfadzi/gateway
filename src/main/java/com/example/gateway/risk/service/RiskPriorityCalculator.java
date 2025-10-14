package com.example.gateway.risk.service;

import com.example.gateway.risk.model.RiskPriority;
import org.springframework.stereotype.Service;

/**
 * Service for calculating priority scores for risk items.
 *
 * Priority scoring is on a 0-100 scale:
 * - Base score from RiskPriority enum (CRITICAL=40, HIGH=30, MEDIUM=20, LOW=10)
 * - Evidence status multiplier (missing/non-compliant = higher score)
 * - Additional factors can be added as needed
 *
 * Higher scores indicate higher priority risks that need immediate attention.
 */
@Service
public class RiskPriorityCalculator {

    /**
     * Calculate priority score for a risk item.
     *
     * @param priority Base priority from registry configuration
     * @param evidenceStatus Status of the evidence (e.g., "missing", "non_compliant", "under_review", "approved")
     * @return Priority score (0-100), higher = more urgent
     */
    public int calculatePriorityScore(RiskPriority priority, String evidenceStatus) {
        if (priority == null) {
            priority = RiskPriority.LOW;  // Default to LOW if not specified
        }

        // Start with base score from priority
        int baseScore = priority.getBaseScore();

        // Apply evidence status multiplier
        double multiplier = getEvidenceStatusMultiplier(evidenceStatus);

        // Calculate final score (capped at 100)
        int finalScore = (int) Math.min(100, baseScore * multiplier);

        return finalScore;
    }

    /**
     * Get multiplier based on evidence status.
     * Missing or non-compliant evidence should have higher scores.
     *
     * @param evidenceStatus The status of the evidence
     * @return Multiplier (typically between 1.0 and 2.5)
     */
    private double getEvidenceStatusMultiplier(String evidenceStatus) {
        if (evidenceStatus == null || evidenceStatus.isEmpty()) {
            return 2.0;  // No evidence status = treat as high priority
        }

        return switch (evidenceStatus.toLowerCase()) {
            case "missing", "not_provided" -> 2.5;           // Highest priority - no evidence at all
            case "non_compliant", "failed" -> 2.3;           // Very high priority - evidence fails requirements
            case "expired" -> 2.0;                            // High priority - evidence is outdated
            case "under_review", "pending" -> 1.5;           // Medium priority - waiting for review
            case "needs_update" -> 1.3;                      // Medium priority - needs refresh
            case "approved", "compliant" -> 1.0;             // Base priority - everything is good
            case "waived", "exempted" -> 0.5;                // Lower priority - explicitly waived
            default -> 1.5;                                  // Default to medium multiplier if unknown
        };
    }

    /**
     * Calculate aggregate priority score for a domain risk based on its risk items.
     * Uses the maximum priority score from all open items.
     *
     * @param maxItemScore Maximum priority score from open risk items
     * @param highPriorityItemCount Count of CRITICAL/HIGH priority items
     * @param openItemCount Total count of open items
     * @return Aggregate priority score for the domain risk
     */
    public int calculateDomainPriorityScore(int maxItemScore, int highPriorityItemCount, int openItemCount) {
        // Start with the maximum individual item score
        int score = maxItemScore;

        // Add bonus for high count of high-priority items (up to +10 points)
        if (highPriorityItemCount > 0) {
            int highPriorityBonus = Math.min(10, highPriorityItemCount * 2);
            score += highPriorityBonus;
        }

        // Add small bonus for high volume of open items (up to +5 points)
        if (openItemCount > 3) {
            int volumeBonus = Math.min(5, (openItemCount - 3));
            score += volumeBonus;
        }

        // Cap at 100
        return Math.min(100, score);
    }

    /**
     * Determine RiskPriority enum from a calculated score.
     *
     * @param score Priority score (0-100)
     * @return Corresponding RiskPriority enum
     */
    public RiskPriority getPriorityFromScore(int score) {
        return RiskPriority.fromScore(score);
    }

    /**
     * Get severity label based on priority score.
     * Can be used for display purposes.
     *
     * @param score Priority score (0-100)
     * @return Severity label (e.g., "critical", "high", "medium", "low")
     */
    public String getSeverityLabel(int score) {
        if (score >= 90) return "critical";
        if (score >= 70) return "high";
        if (score >= 40) return "medium";
        return "low";
    }

    /**
     * Check if a risk item score indicates it needs immediate attention.
     *
     * @param score Priority score (0-100)
     * @return true if score is >= 70 (HIGH or CRITICAL)
     */
    public boolean requiresImmediateAttention(int score) {
        return score >= 70;
    }
}
