package com.example.gateway.track.dto;

import com.example.gateway.util.FlexibleOffsetDateTimeDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

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
        @JsonDeserialize(using = FlexibleOffsetDateTimeDeserializer.class)
        OffsetDateTime openedAt
) {}