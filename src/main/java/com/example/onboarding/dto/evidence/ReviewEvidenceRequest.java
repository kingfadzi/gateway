package com.example.onboarding.dto.evidence;

public record ReviewEvidenceRequest(
    String action,       // "approve" | "reject"
    String reviewerId,   // SME or system id
    String notes         // optional free text (not stored yet; add column if needed)
) {}
