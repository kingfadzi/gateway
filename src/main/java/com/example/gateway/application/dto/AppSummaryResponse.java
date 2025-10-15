// src/main/java/com/example/onboarding/dto/application/AppSummaryResponse.java
package com.example.gateway.application.dto;

public record AppSummaryResponse(
    String appId,
    String name,
    String appCriticalityAssessment,
    String businessServiceName,
    String applicationType,
    String install_type,
    String architecture_type,
    RiskMetrics riskMetrics  // Optional: null if not requested via includeRiskMetrics=true
) {
    /**
     * Constructor for backward compatibility (without risk metrics).
     */
    public AppSummaryResponse(
            String appId,
            String name,
            String appCriticalityAssessment,
            String businessServiceName,
            String applicationType,
            String install_type,
            String architecture_type) {
        this(appId, name, appCriticalityAssessment, businessServiceName,
             applicationType, install_type, architecture_type, null);
    }
}
