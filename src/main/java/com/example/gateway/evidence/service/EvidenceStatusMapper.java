package com.example.gateway.evidence.service;

import com.example.gateway.evidence.model.EvidenceFieldLinkStatus;
import org.springframework.stereotype.Component;

/**
 * Maps EvidenceFieldLinkStatus to evidence status strings used for risk priority calculation.
 *
 * Evidence status affects priority score multipliers:
 * - submitted/under_review: 1.0 (full priority - evidence pending review)
 * - rejected: 0.9 (high priority - evidence was rejected)
 * - expired: 0.8 (medium-high priority - evidence needs refresh)
 * - approved: 0.5 (lower priority - evidence accepted)
 * - missing: 1.0 (full priority - no evidence submitted)
 */
@Component
public class EvidenceStatusMapper {

    /**
     * Map EvidenceFieldLinkStatus to evidence status for risk calculation.
     *
     * @param linkStatus The evidence field link status
     * @return Evidence status string for priority calculation
     */
    public String mapLinkStatusToEvidenceStatus(EvidenceFieldLinkStatus linkStatus) {
        if (linkStatus == null) {
            return "missing";
        }

        return switch (linkStatus) {
            case APPROVED, USER_ATTESTED -> "approved";
            case REJECTED -> "rejected";
            case PENDING_PO_REVIEW, PENDING_SME_REVIEW, PENDING_REVIEW, ATTACHED -> "submitted";
        };
    }

    /**
     * Determine evidence status considering both link status and validity dates.
     * This method can be enhanced to check if evidence is expired.
     *
     * @param linkStatus The evidence field link status
     * @param validUntil The evidence expiration date (null if not set)
     * @return Evidence status string for priority calculation
     */
    public String determineEvidenceStatus(EvidenceFieldLinkStatus linkStatus,
                                         java.time.OffsetDateTime validUntil) {
        // Check if evidence is expired
        if (validUntil != null && validUntil.isBefore(java.time.OffsetDateTime.now())) {
            return "expired";
        }

        // Otherwise map from link status
        return mapLinkStatusToEvidenceStatus(linkStatus);
    }
}
