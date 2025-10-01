package com.example.gateway.claims.dto;

/**
 * Request DTO for attaching evidence to a claim
 */
public record AttachEvidenceRequest(
        String documentId,      // optional document reference
        String docVersionId     // optional document version reference
) {}