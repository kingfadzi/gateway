package com.example.gateway.sme.dto;

import com.example.gateway.risk.model.RiskStatus;
import com.example.gateway.risk.model.RiskStory;

import java.time.OffsetDateTime;

public record SmeCrossDomainRiskResponse(
    String riskId,
    String appId,
    String title,
    String severity,
    RiskStatus status,
    String fieldKey,
    String domain,
    OffsetDateTime assignedAt,
    String appName
) {
    
    public static SmeCrossDomainRiskResponse fromRiskStory(RiskStory riskStory, String appName, String domain) {
        return new SmeCrossDomainRiskResponse(
            riskStory.getRiskId(),
            riskStory.getAppId(),
            riskStory.getTitle(),
            riskStory.getSeverity(),
            riskStory.getStatus(),
            riskStory.getFieldKey(),
            domain,
            riskStory.getAssignedAt(),
            appName
        );
    }
}