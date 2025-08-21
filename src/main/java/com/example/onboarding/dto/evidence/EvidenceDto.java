package com.example.onboarding.dto.evidence;

import java.time.OffsetDateTime;

public record EvidenceDto(
        String evidenceId,
        String profileFieldId,
        String profileFieldKey,     // from join to profile_field
        String uri,
        String type,
        String sha256,
        String sourceSystem,
        String status,              // active | superseded | revoked
        OffsetDateTime validFrom,
        OffsetDateTime validUntil,
        OffsetDateTime revokedAt,
        OffsetDateTime addedAt,     // legacy column, still useful
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
