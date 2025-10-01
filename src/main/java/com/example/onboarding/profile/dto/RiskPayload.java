package com.example.onboarding.profile.dto;

public record RiskPayload(
        String risk_id,
        String profile_field_id,
        String title,
        String severity,
        String status
) {
}