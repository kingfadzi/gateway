package com.example.onboarding.dto;

import java.time.OffsetDateTime;

public record UpdateAppRequest(
        String parentAppId,
        String name,
        String appCriticalityAssessment,
        String jiraBacklogId,
        String leanControlServiceId,
        String repoId,
        String operationalStatus,
        String onboardingStatus,
        String ownerId,
        OffsetDateTime expectedUpdatedAt // optimistic concurrency
) {}
