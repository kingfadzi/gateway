package com.example.gateway.track.dto;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Track summary for list views
 */
public record TrackSummary(
        String trackId,
        String appId,
        String title,
        String intent,
        String status,
        String result,
        OffsetDateTime openedAt,
        OffsetDateTime closedAt,
        String provider,
        String resourceType,
        String resourceId,
        String uri,
        Map<String, Object> attributes,
        OffsetDateTime updatedAt
) {}