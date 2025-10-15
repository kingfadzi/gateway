package com.example.gateway.risk.dto;

/**
 * Response for GET /api/v1/domain-risks/arb/{arbName}/metrics
 * Dashboard-level metrics for HUD cards.
 * All counts are based on risk_item table (evidence-level granularity).
 */
public record DashboardMetricsResponse(
    String scope,                      // my-queue, my-domain, all-domains
    String arbName,
    String userId,                     // Only for my-queue scope
    Integer criticalCount,             // Count of CRITICAL priority items (all active states)
    Integer openItemsCount,            // Count of items with active status
    Integer pendingReviewCount,        // Count of risk items with status=PENDING_REVIEW (awaiting triage)
    Double averageRiskScore,           // Average priorityScore (active states)
    String healthGrade,                // A/B/C/D/F based on averageRiskScore
    RecentActivityMetrics recentActivity
) {
}
