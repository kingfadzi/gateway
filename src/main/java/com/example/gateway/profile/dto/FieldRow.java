package com.example.gateway.profile.dto;

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