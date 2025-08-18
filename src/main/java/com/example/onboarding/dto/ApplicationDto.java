package com.example.onboarding.dto;

import java.time.OffsetDateTime;

public record ApplicationDto(
        String appId,
        String parentAppId,
        String name,
        String appCriticalityAssessment,
        String jiraBacklogId,
        String leanControlServiceId,
        String repoId,
        String operationalStatus,
        String onboardingStatus,
        String ownerId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        Boolean hasChildren // for list rows
) {}
