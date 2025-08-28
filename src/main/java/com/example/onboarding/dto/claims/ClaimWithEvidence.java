package com.example.onboarding.dto.claims;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Full claim details with attached evidence
 */
public record ClaimWithEvidence(
        String claimId,
        String appId,
        String fieldKey,
        String method,
        String status,
        String scopeType,
        String scopeId,
        String trackId,
        OffsetDateTime submittedAt,
        OffsetDateTime reviewedAt,
        OffsetDateTime assignedAt,
        String comment,
        Map<String, Object> decisionJson,
        List<EvidenceAttachment> evidence,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    /**
     * Evidence attached to this claim
     */
    public record EvidenceAttachment(
            String evidenceId,
            String uri,
            String type,
            String sha256,
            String sourceSystem,
            OffsetDateTime validFrom,
            OffsetDateTime validUntil,
            String status,
            String documentId,
            String docVersionId,
            OffsetDateTime addedAt
    ) {}
}