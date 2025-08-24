package com.example.onboarding.dto.profile;

public record RiskPayload(
        String risk_id,
        String profile_field_id,
        String title,
        String severity,
        String status
) {
}