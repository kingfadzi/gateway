package com.example.onboarding.profile.dto;

import java.time.OffsetDateTime;

public record ProfileField(
        String fieldId,
        String fieldKey,           // renamed from `key`
        Object value,              // maps from jsonb (Jackson will materialize Map/String/Number/etc.)
        String sourceSystem,
        String sourceRef,
        int evidenceCount,
        OffsetDateTime updatedAt
) {}
