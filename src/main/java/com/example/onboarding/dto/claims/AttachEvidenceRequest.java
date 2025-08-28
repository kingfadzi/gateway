package com.example.onboarding.dto.claims;

/**
 * Request DTO for attaching evidence to a claim
 */
public record AttachEvidenceRequest(
        String documentId,      // optional document reference
        String docVersionId     // optional document version reference
) {}