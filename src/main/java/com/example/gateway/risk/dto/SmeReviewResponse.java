package com.example.gateway.risk.dto;

import java.time.OffsetDateTime;

/**
 * Response DTO for SME review actions on risk items.
 */
public record SmeReviewResponse(
        String riskId,
        String status,         // e.g., "SME_APPROVED", "SME_REJECTED", "INFO_REQUESTED", etc.
        String reviewedBy,
        OffsetDateTime reviewedAt
) {
}
