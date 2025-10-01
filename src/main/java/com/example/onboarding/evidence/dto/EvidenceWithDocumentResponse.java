package com.example.onboarding.evidence.dto;

import com.example.onboarding.document.dto.DocumentResponse;
import java.time.OffsetDateTime;

/**
 * Response DTO for evidence creation that includes the created document details
 */
public record EvidenceWithDocumentResponse(
        String evidenceId,
        String appId,
        String profileFieldId,
        String claimId,
        String uri,
        String type,
        String sha256,
        String sourceSystem,
        String submittedBy,
        OffsetDateTime validFrom,
        OffsetDateTime validUntil,
        String status,
        OffsetDateTime revokedAt,
        String reviewedBy,
        OffsetDateTime reviewedAt,
        String relatedEvidenceFields,
        String trackId,
        String documentId,
        String docVersionId,
        OffsetDateTime addedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        DocumentResponse document,          // Embedded document details
        // Auto-risk creation fields
        boolean riskWasCreated,
        String autoCreatedRiskId,
        String assignedSme
) {}