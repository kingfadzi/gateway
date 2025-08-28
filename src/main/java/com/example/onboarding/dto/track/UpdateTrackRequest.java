package com.example.onboarding.dto.track;

import java.time.OffsetDateTime;

/**
 * Request DTO for updating an existing track
 */
public record UpdateTrackRequest(
        String title,
        String status,        // 'open'|'in_review'|'closed'
        String result,        // 'pass'|'fail'|'waived'|'abandoned'
        OffsetDateTime closedAt
) {}