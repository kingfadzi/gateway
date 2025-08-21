package com.example.onboarding.dto.evidence;

import java.time.OffsetDateTime;

public record EvidenceDto(
        String evidenceId,
        String profileFieldId,
        String profileFieldKey,   // pf.key AS profile_field_key
        String uri,
        String type,
        String sha256,
        String sourceSystem,
        String submittedBy,       // NEW
        String status,
        OffsetDateTime validFrom,
        OffsetDateTime validUntil,
        OffsetDateTime revokedAt,
        String reviewedBy,        // NEW
        OffsetDateTime reviewedAt,// NEW
        String tags,              // NEW
        OffsetDateTime addedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
