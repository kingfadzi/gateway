package com.example.onboarding.dto.risk;

import java.time.OffsetDateTime;

public record RiskStory(
        String riskKey,
        String domain,
        String status,
        String scopeType,
        String scopeId,
        String releaseId,
        OffsetDateTime slaDue,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
