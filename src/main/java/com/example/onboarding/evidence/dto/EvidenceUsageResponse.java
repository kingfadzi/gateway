package com.example.onboarding.evidence.dto;

import java.util.List;

/**
 * Response showing how evidence is used across fields and risks
 */
public record EvidenceUsageResponse(
        String evidenceId,
        String title,
        String url,
        List<FieldUsage> fieldUsages,
        List<RiskUsage> riskUsages
) {
    
    public record FieldUsage(
            String profileFieldId,
            String fieldKey,
            String appId,
            String linkStatus,
            String linkedBy,
            String linkedAt
    ) {}
    
    public record RiskUsage(
            String riskId,
            String appId,
            String fieldKey,
            String status,
            String assignedSme,
            boolean isTriggering  // True if evidence triggered this risk
    ) {}
}