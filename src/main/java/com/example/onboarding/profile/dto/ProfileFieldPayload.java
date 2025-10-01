package com.example.onboarding.profile.dto;

public record ProfileFieldPayload(
        String id,
        String profile_id,
        String field_key,
        Object value,
        String derived_from
) {
}