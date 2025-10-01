package com.example.onboarding.profile.dto;

import java.util.List;

public record FieldGraphPayload(
        String profileFieldId,
        String fieldKey,
        String label,
        Object policyRequirement,
        List<EvidenceGraphPayload> evidence,
        String approvalStatus,    // missing, pending, approved, user_attested, rejected
        String freshnessStatus,   // current, expiring, expired, broken
        List<RiskGraphPayload> risks,
        List<AttestationGraphPayload> attestations  // Evidence awaiting PO review
) {
}