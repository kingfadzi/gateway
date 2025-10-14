package com.example.gateway.risk.dto;

/**
 * Application-level velocity metrics for ARB dashboard.
 * Tracks how many applications are experiencing risk activity (creation/resolution).
 *
 * Design rationale:
 * - Focuses on app counts rather than individual risk item counts
 * - Provides both 7-day and 30-day windows for trend analysis
 * - Helps ARBs understand portfolio-level activity patterns
 */
public record ApplicationActivityMetrics(
    // 7-day activity window
    Integer appsWithNewRisks7d,       // Apps that had new risks created in last 7 days
    Integer appsWithResolutions7d,    // Apps that had risks resolved in last 7 days

    // 30-day activity window
    Integer appsWithNewRisks30d,      // Apps that had new risks created in last 30 days
    Integer appsWithResolutions30d    // Apps that had risks resolved in last 30 days
) {
}
