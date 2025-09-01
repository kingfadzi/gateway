package com.example.onboarding.dto.sme;

import com.example.onboarding.model.RiskStatus;
import com.example.onboarding.model.risk.RiskStory;
import com.example.onboarding.dto.profile.ProfileFieldTypeInfo;
import com.example.onboarding.service.profile.ProfileFieldRegistryService;

import java.time.OffsetDateTime;
import java.time.Duration;

public record SmeRiskQueueResponse(
    String riskId,
    String appId,
    String title,
    String severity,
    RiskStatus status,
    String fieldKey,
    OffsetDateTime assignedAt,
    OffsetDateTime dueDate,
    String appName,
    String domain
) {
    
    public static SmeRiskQueueResponse fromRiskStory(RiskStory riskStory, String appName, String domain, OffsetDateTime dueDate) {
        return new SmeRiskQueueResponse(
            riskStory.getRiskId(),
            riskStory.getAppId(),
            riskStory.getTitle(),
            riskStory.getSeverity(),
            riskStory.getStatus(),
            riskStory.getFieldKey(),
            riskStory.getAssignedAt(),
            dueDate,
            appName,
            domain
        );
    }
    
    /**
     * Calculate due date from assignedAt + TTL from policy requirement snapshot
     */
    public static OffsetDateTime calculateDueDate(RiskStory riskStory) {
        if (riskStory.getAssignedAt() == null) {
            return null;
        }
        
        // Try to get TTL from policy requirement snapshot
        if (riskStory.getPolicyRequirementSnapshot() != null) {
            Object activeRule = riskStory.getPolicyRequirementSnapshot().get("activeRule");
            if (activeRule instanceof java.util.Map) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> ruleMap = (java.util.Map<String, Object>) activeRule;
                Object ttl = ruleMap.get("ttl");
                if (ttl instanceof String) {
                    Duration duration = parseTtl((String) ttl);
                    if (duration != null) {
                        return riskStory.getAssignedAt().plus(duration);
                    }
                }
            }
        }
        
        // Default to 30 days if no TTL found
        return riskStory.getAssignedAt().plusDays(30);
    }
    
    /**
     * Parse TTL string like "90d", "7d", etc.
     */
    private static Duration parseTtl(String ttl) {
        if (ttl == null || ttl.isEmpty()) {
            return null;
        }
        
        try {
            if (ttl.endsWith("d")) {
                int days = Integer.parseInt(ttl.substring(0, ttl.length() - 1));
                return Duration.ofDays(days);
            } else if (ttl.endsWith("h")) {
                int hours = Integer.parseInt(ttl.substring(0, ttl.length() - 1));
                return Duration.ofHours(hours);
            }
        } catch (NumberFormatException e) {
            // Fall through to return null
        }
        
        return null;
    }
}