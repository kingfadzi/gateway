package com.example.onboarding.dto.profile;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record ProfilePayload(
        String appId,
        String name,
        OffsetDateTime updatedAt,
        Map<String, String> drivers,
        List<ProfileFieldPayload> fields,
        List<EvidencePayload> evidence,
        List<RiskPayload> risks
) {
}