package com.example.gateway.risk.dto;

import java.util.Map;

/**
 * App-centric metrics response for ARB dashboard overview.
 * Focuses on counting applications at risk rather than individual risk items.
 *
 * Design rationale:
 * - Primary metrics count applications (e.g., "15 apps at risk" vs "156 risk items")
 * - Volume metrics provide context about item counts
 * - Velocity metrics track app-level activity trends
 *
 * Used by: GET /api/v1/domain-risks/arb/{arbName}/app-metrics?scope={scope}&userId={userId}
 */
public record ApplicationCentricMetricsResponse(
    String scope,                           // my-queue, my-domain, all-domains
    String arbName,
    String userId,                          // Only populated for my-queue scope

    // === PRIMARY METRICS (Application Counts) ===
    Integer applicationsAtRisk,             // Count of unique apps with active risks
    Integer criticalApplications,           // Count of apps with ≥1 CRITICAL item
    Integer highRiskApplications,           // Count of apps with score ≥70
    Integer applicationsAwaitingTriage,     // Count of apps with ≥1 OPEN item

    // === VOLUME METRICS (Item Counts for Context) ===
    Integer totalOpenItems,                 // Total count of OPEN + IN_PROGRESS items
    Double averageItemsPerApp,              // Concentration indicator

    // === RISK LEVEL DISTRIBUTION ===
    Map<String, Integer> applicationsByRiskLevel,  // CRITICAL/HIGH/MEDIUM/LOW → app count

    // === VELOCITY METRICS (App-Level Activity) ===
    ApplicationActivityMetrics recentActivity,

    // === HEALTH GRADE ===
    String healthGrade                      // A/B/C/D/F based on % critical apps
) {
    /**
     * Calculate health grade based on percentage of critical applications.
     *
     * Grading scale:
     * - A: 0-5% critical apps
     * - B: 5-15% critical apps
     * - C: 15-30% critical apps
     * - D: 30-50% critical apps
     * - F: 50%+ critical apps
     */
    public static String calculateHealthGrade(int criticalApps, int totalApps) {
        if (totalApps == 0) return "A";

        double criticalPercentage = (double) criticalApps / totalApps * 100;

        if (criticalPercentage < 5) return "A";
        if (criticalPercentage < 15) return "B";
        if (criticalPercentage < 30) return "C";
        if (criticalPercentage < 50) return "D";
        return "F";
    }
}
