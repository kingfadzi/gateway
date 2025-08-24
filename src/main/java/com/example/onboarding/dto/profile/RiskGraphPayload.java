package com.example.onboarding.dto.profile;

public record RiskGraphPayload(
        String risk_id,
        String title,
        String severity,
        String status
) {
}