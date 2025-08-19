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
        String transactionCycle,
        String applicationType,
        String applicationTier,
        String architectureType,
        String installType,
        String housePosition,
        String productOwner,
        String productOwnerBrid,
        String onboardingStatus,
        String ownerId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        Boolean hasChildren
) {}
