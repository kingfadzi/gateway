package com.example.gateway.profile.dto;

import java.util.List;

public record PatchProfileResponse(
        Integer version,
        String profileId,
        List<UpdatedField> updatedFields
) {
    public record UpdatedField(
            String fieldId,
            String fieldKey,
            Object value
    ) {}
}
