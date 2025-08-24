package com.example.onboarding.dto.profile;

import java.time.OffsetDateTime;

public record FieldRow(
        String fieldId, 
        String fieldKey, 
        String valueJson, 
        String sourceSystem, 
        String sourceRef,
        int evidenceCount, 
        OffsetDateTime updatedAt
) {
}