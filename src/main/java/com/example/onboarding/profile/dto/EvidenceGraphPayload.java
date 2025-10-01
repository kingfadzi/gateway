package com.example.onboarding.profile.dto;

import java.time.OffsetDateTime;

public record EvidenceGraphPayload(
        String evidence_id,
        String status,
        OffsetDateTime valid_until,
        String reviewed_by
) {
}