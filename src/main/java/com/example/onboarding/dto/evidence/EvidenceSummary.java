package com.example.onboarding.dto.evidence;

import java.time.OffsetDateTime;

/**
 * Summary view of evidence for list operations
 */
public record EvidenceSummary(
        String evidenceId,
        String appId,
        String profileFieldId,
        String claimId,
        String uri,
        String type,
        String status,
        String submittedBy,
        OffsetDateTime validFrom,
        OffsetDateTime validUntil,
        String trackId,
        String documentId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}