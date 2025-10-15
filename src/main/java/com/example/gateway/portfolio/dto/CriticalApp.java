package com.example.gateway.portfolio.dto;

/**
 * Represents an application with critical severity risks.
 * Used in portfolio risk summary dashboard.
 */
public record CriticalApp(
    String appId,
    String appName,
    int criticalCount,
    int highCount,
    int riskScore
) {}
