package com.example.onboarding.application.dto;

import java.time.OffsetDateTime;

public record UpdateAppRequest(
        String parentAppId,
        String name,
        String appCriticalityAssessment,
        String businessServiceName,
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
        OffsetDateTime expectedUpdatedAt
) {}
