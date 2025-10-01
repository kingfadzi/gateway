package com.example.gateway.track.dto;

import com.example.gateway.util.FlexibleOffsetDateTimeDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.time.OffsetDateTime;

/**
 * Request DTO for updating an existing track
 */
public record UpdateTrackRequest(
        String title,
        String status,        // 'open'|'in_review'|'closed'
        String result,        // 'pass'|'fail'|'waived'|'abandoned'
        @JsonDeserialize(using = FlexibleOffsetDateTimeDeserializer.class)
        OffsetDateTime closedAt
) {}