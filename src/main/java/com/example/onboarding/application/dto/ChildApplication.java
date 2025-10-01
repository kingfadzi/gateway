package com.example.onboarding.application.dto;

public record ChildApplication(
        String appId,
        String name,
        String appCriticalityAssessment,
        String applicationType,
        String installType,
        String architectureType
) {}