package com.example.onboarding.dto;

public record CreateAppRequest(
        String parentAppId,
        String name,
        String appCriticalityAssessment,
        String jiraBacklogId,
        String leanControlServiceId,
        String repoId,
        String operationalStatus,
        String onboardingStatus,
        String ownerId
) {}
