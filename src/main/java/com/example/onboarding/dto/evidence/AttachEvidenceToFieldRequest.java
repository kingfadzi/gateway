package com.example.onboarding.dto.evidence;

/**
 * Request to attach evidence to a profile field
 */
public record AttachEvidenceToFieldRequest(
        String linkedBy,          // Who is attaching the evidence (PO user ID)
        String comment           // Optional comment about the attachment
) {
}