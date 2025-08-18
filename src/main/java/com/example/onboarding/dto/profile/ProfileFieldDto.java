package com.example.onboarding.dto.profile;

import java.time.OffsetDateTime;

public record ProfileFieldDto(
        String fieldId,
        String fieldKey,           // renamed from `key`
        Object value,              // maps from jsonb (Jackson will materialize Map/String/Number/etc.)
        String sourceSystem,
        String sourceRef,
        int evidenceCount,
        OffsetDateTime lastUpdated
) {}
