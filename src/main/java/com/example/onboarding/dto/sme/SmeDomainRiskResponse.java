package com.example.onboarding.dto.sme;

import com.example.onboarding.model.RiskStatus;
import com.example.onboarding.model.risk.RiskStory;

import java.time.OffsetDateTime;

public record SmeDomainRiskResponse(
    String riskId,
    String appId,
    String title,
    String severity,
    RiskStatus status,
    String fieldKey,
    OffsetDateTime assignedAt,
    OffsetDateTime lastReviewedAt,
    String appName
) {
    
    public static SmeDomainRiskResponse fromRiskStory(RiskStory riskStory, String appName) {
        return new SmeDomainRiskResponse(
            riskStory.getRiskId(),
            riskStory.getAppId(),
            riskStory.getTitle(),
            riskStory.getSeverity(),
            riskStory.getStatus(),
            riskStory.getFieldKey(),
            riskStory.getAssignedAt(),
            riskStory.getReviewedAt(),
            appName
        );
    }
}