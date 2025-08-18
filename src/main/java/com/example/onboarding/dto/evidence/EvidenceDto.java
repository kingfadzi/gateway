package com.example.onboarding.dto.evidence;

import java.time.OffsetDateTime;

public record EvidenceDto(
        String evidenceId,
        String profileFieldId,
        String uri,
        String type,
        OffsetDateTime addedAt
) {}
