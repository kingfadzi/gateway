package com.example.onboarding.dto.application;

public record ChildApplication(
        String appId,
        String name,
        String appCriticalityAssessment,
        String applicationType,
        String installType,
        String architectureType
) {}