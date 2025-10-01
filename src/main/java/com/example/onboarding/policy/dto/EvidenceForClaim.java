package com.example.onboarding.policy.dto;

import java.time.OffsetDateTime;

public record EvidenceForClaim(
        String evidenceId,
        String appId,              // p.app_id
        String profileFieldId,     // e.profile_field_id
        String profileFieldKey,    // f.field_key (e.g., app_criticality)
        String type,               // e.type
        OffsetDateTime validFrom,  // e.valid_from
        OffsetDateTime validUntil, // e.valid_until
        OffsetDateTime revokedAt,  // e.revoked_at
        String uri                 // e.uri
    ) {}