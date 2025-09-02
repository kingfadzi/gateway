package com.example.onboarding.dto.profile;

import java.time.OffsetDateTime;

/**
 * Attestation information for profile field graph (evidence with PENDING_PO_REVIEW status)
 */
public record AttestationGraphPayload(
        String evidenceId,
        String documentTitle,
        String documentSourceType,
        OffsetDateTime linkedAt,
        String submittedBy
) {
}