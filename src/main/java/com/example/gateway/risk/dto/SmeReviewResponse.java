package com.example.gateway.risk.dto;

import java.time.OffsetDateTime;

/**
 * Response DTO for SME review actions on risk items.
 */
public record SmeReviewResponse(
        String riskId,
        String status,         // e.g., "SME_APPROVED", "SME_REJECTED", "INFO_REQUESTED", "VALIDATION_ERROR", etc.
        String reviewedBy,
        OffsetDateTime reviewedAt,
        String errorMessage    // Optional error message (null for successful operations)
) {
    /**
     * Constructor for successful operations (no error message).
     */
    public SmeReviewResponse(String riskId, String status, String reviewedBy, OffsetDateTime reviewedAt) {
        this(riskId, status, reviewedBy, reviewedAt, null);
    }
}
