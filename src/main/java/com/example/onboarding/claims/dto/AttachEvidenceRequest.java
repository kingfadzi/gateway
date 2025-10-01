package com.example.onboarding.claims.dto;

/**
 * Request DTO for attaching evidence to a claim
 */
public record AttachEvidenceRequest(
        String documentId,      // optional document reference
        String docVersionId     // optional document version reference
) {}