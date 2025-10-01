package com.example.gateway.profile.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record ProfilePayload(
        String appId,
        String name,
        int version,
        OffsetDateTime updatedAt,
        Map<String, String> drivers,
        List<ProfileFieldPayload> fields,
        List<EvidencePayload> evidence,
        List<RiskPayload> risks
) {
}