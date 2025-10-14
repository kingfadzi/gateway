package com.example.gateway.risk.dto;

/**
 * Recent activity metrics for risk items.
 * Tracks new and resolved risks in 7-day and 30-day windows.
 */
public record RecentActivityMetrics(
    Integer newRisksLast7Days,
    Integer resolvedLast7Days,
    Integer newRisksLast30Days,
    Integer resolvedLast30Days
) {
    public static RecentActivityMetrics empty() {
        return new RecentActivityMetrics(0, 0, 0, 0);
    }
}
