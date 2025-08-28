package com.example.onboarding.dto.track;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Request DTO for creating a new track
 */
public record CreateTrackRequest(
        String title,
        String intent,        // 'compliance'|'risk'|'security'|'architecture'
        String provider,      // 'jira'|'snow'|'gitlab'|'manual'|'policy'
        String resourceType,  // 'epic'|'change'|'mr'|'note'|'control'
        String resourceId,    // native external key
        String uri,           // canonical link
        Map<String, Object> attributes,  // raw payload/fields
        OffsetDateTime openedAt
) {}