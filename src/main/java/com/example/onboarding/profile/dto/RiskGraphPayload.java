package com.example.onboarding.profile.dto;

public record RiskGraphPayload(
        String risk_id,
        String title,
        String severity,
        String status
) {
}