package com.example.gateway.risk.dto;

/**
 * Response for GET /api/v1/domain-risks/arb/{arbName}/metrics
 * Dashboard-level metrics for HUD cards.
 */
public record DashboardMetricsResponse(
    String scope,                      // my-queue, my-domain, all-domains
    String arbName,
    String userId,                     // Only for my-queue scope
    Integer criticalCount,             // Count of CRITICAL priority items (OPEN/IN_PROGRESS)
    Integer openItemsCount,            // Count of items with status=OPEN
    Integer pendingReviewCount,        // Count of domain risks with status=PENDING_ARB_REVIEW
    Double averageRiskScore,           // Average priorityScore (OPEN/IN_PROGRESS)
    String healthGrade,                // A/B/C/D/F based on averageRiskScore
    RecentActivityMetrics recentActivity
) {
}
