package com.example.onboarding.profile.dto;

import java.util.List;

public record PatchProfileRequest(
        Integer version,                   // optional: target profile version; null -> latest or create v1
        List<FieldPatch> fields
) {
    public record FieldPatch(
            String key,                   // logical key (will be stored in field_key)
            Object value,                 // jsonb value
            String sourceSystem,
            String sourceRef
    ) {}
}
