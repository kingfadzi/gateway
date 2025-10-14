package com.example.gateway.risk.dto;

import java.util.List;
import java.util.Map;

/**
 * Comprehensive dashboard response for ARB/SME view.
 * Provides all metrics needed for a dashboard visualization.
 */
public record ArbDashboardResponse(
        // Overview metrics
        String arbName,
        OverviewMetrics overview,

        // Domain breakdown
        List<DomainBreakdown> domains,

        // Application breakdown (top apps with most risks)
        List<AppBreakdown> topApplications,

        // Status distribution
        Map<String, Long> statusDistribution,

        // Priority distribution
        PriorityDistribution priorityDistribution,

        // Recent activity
        RecentActivity recentActivity
) {

    public record OverviewMetrics(
            long totalDomainRisks,
            long totalOpenItems,
            long criticalCount,      // priority >= 90
            long highCount,          // priority >= 70
            long averagePriorityScore,
            long needsImmediateAttention  // priority >= 70
    ) {}

    public record DomainBreakdown(
            String riskDimension,
            long riskCount,
            long openItems,
            long criticalItems,
            double avgPriorityScore,
            String topPriorityStatus  // Status of highest priority risk
    ) {}

    public record AppBreakdown(
            String appId,
            String appName,         // If available from app service
            long domainRiskCount,
            long totalOpenItems,
            int highestPriorityScore,
            String criticalDomain   // Domain with highest priority
    ) {}

    public record PriorityDistribution(
            long critical,          // 90-100
            long high,              // 70-89
            long medium,            // 40-69
            long low                // 0-39
    ) {}

    public record RecentActivity(
            long newRisksLast7Days,
            long newRisksLast30Days,
            long resolvedLast7Days,
            long resolvedLast30Days
    ) {}
}
