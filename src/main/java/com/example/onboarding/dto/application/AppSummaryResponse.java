// src/main/java/com/example/onboarding/dto/application/AppSummaryResponse.java
package com.example.onboarding.dto.application;

public record AppSummaryResponse(
    String appId,
    String name,
    String appCriticalityAssessment,
    String businessServiceName,
    String install_type,
    String architecture_type
) {}
