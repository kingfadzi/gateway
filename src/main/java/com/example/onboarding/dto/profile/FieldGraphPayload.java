package com.example.onboarding.dto.profile;

import java.util.List;

public record FieldGraphPayload(
        String fieldKey,
        String label,
        Object policyRequirement,
        List<EvidenceGraphPayload> evidence,
        String assurance,
        List<RiskGraphPayload> risks
) {
}