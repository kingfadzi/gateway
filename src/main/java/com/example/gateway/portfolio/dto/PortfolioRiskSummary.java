package com.example.gateway.portfolio.dto;

import java.util.List;

/**
 * Portfolio-level risk summary for Product Owner dashboard.
 * Provides actionable metrics and critical app highlights.
 */
public record PortfolioRiskSummary(
    int actionRequired,
    int blockingCompliance,
    int missingEvidence,
    int pendingReview,
    int escalated,
    int recentWins,
    List<CriticalApp> criticalApps,
    int totalApps,
    int appsWithRisks
) {}
