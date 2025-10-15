package com.example.gateway.risk.service;

import com.example.gateway.application.dto.RiskMetrics;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Service for calculating risk metrics aggregations.
 * Delegates to ArbDashboardService to ensure 100% consistency between SME and PO views.
 * This service exists as a thin wrapper to maintain the existing API contract.
 */
@Service
public class RiskMetricsService {

    private final ArbDashboardService arbDashboardService;

    public RiskMetricsService(ArbDashboardService arbDashboardService) {
        this.arbDashboardService = arbDashboardService;
    }

    /**
     * Calculate risk metrics for a single application.
     * Delegates to ArbDashboardService to use the EXACT SAME logic as SME dashboard.
     *
     * @param appId Application ID
     * @return Risk metrics for the application
     */
    public RiskMetrics calculateForApp(String appId) {
        Map<String, RiskMetrics> metricsMap = calculateForApps(List.of(appId));
        return metricsMap.getOrDefault(appId, RiskMetrics.empty());
    }

    /**
     * Calculate risk metrics for multiple applications efficiently using batch queries.
     * Delegates to ArbDashboardService to ensure 100% consistency with SME calculations.
     * No duplicate logic - single source of truth.
     *
     * @param appIds List of application IDs
     * @return Map of appId to RiskMetrics
     */
    public Map<String, RiskMetrics> calculateForApps(List<String> appIds) {
        // Delegate to ArbDashboardService - single source of truth for risk calculations
        return arbDashboardService.calculateRiskMetricsForApps(appIds);
    }
}
