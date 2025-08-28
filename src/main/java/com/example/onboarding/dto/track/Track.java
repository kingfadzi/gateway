package com.example.onboarding.dto.track;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Track entity representing a piece of compliance work
 */
public record Track(
        String trackId,
        String appId,
        String title,
        String intent,        // 'compliance'|'risk'|'security'|'architecture'
        String status,        // 'open'|'in_review'|'closed'
        String result,        // 'pass'|'fail'|'waived'|'abandoned'
        OffsetDateTime openedAt,
        OffsetDateTime closedAt,
        String provider,      // 'jira'|'snow'|'gitlab'|'manual'|'policy'
        String resourceType,  // 'epic'|'change'|'mr'|'note'|'control'
        String resourceId,    // native external key
        String uri,           // canonical link
        Map<String, Object> attributes,  // raw payload/fields
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}