package com.example.onboarding.profile.dto;

import java.time.OffsetDateTime;

public record EvidencePayload(
        String evidence_id,
        String profile_field_id,
        String uri,
        String status,
        String reviewed_by,
        OffsetDateTime reviewed_at,
        OffsetDateTime valid_from,
        OffsetDateTime valid_until
) {
}