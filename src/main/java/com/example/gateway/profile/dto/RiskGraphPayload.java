package com.example.gateway.profile.dto;

public record RiskGraphPayload(
        String risk_id,
        String title,
        String severity,
        String status
) {
}