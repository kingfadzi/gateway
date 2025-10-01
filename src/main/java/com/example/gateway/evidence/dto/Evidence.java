package com.example.gateway.evidence.dto;

import java.time.OffsetDateTime;

/**
 * Evidence entity aligned with current database schema
 */
public record Evidence(
        String evidenceId,
        String appId,
        String profileFieldId,
        String claimId,             // optional reference to control_claim
        String uri,
        String type,
        String sha256,
        String sourceSystem,
        String submittedBy,
        OffsetDateTime validFrom,
        OffsetDateTime validUntil,
        String status,              // 'active'|'superseded'|'revoked'
        OffsetDateTime revokedAt,
        String reviewedBy,
        OffsetDateTime reviewedAt,
        String relatedEvidenceFields,
        String trackId,             // optional track binding
        String documentId,          // optional document reference
        String docVersionId,        // optional document version reference
        OffsetDateTime addedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}