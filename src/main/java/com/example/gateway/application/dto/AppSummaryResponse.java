// src/main/java/com/example/onboarding/dto/application/AppSummaryResponse.java
package com.example.gateway.application.dto;

public record AppSummaryResponse(
    String appId,
    String name,
    String appCriticalityAssessment,
    String businessServiceName,
    String applicationType,
    String install_type,
    String architecture_type
) {}
