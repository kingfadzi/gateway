package com.example.gateway.evidence.dto;

import com.example.gateway.util.FlexibleOffsetDateTimeDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.time.OffsetDateTime;

/**
 * Request DTO for creating new evidence
 */
public record CreateEvidenceRequest(
        String profileFieldId,      // required - links to profile_field
        String uri,                 // required - evidence location/link
        String type,                // optional - evidence type (file, link, etc.)
        String sourceSystem,        // optional - source system identifier
        String submittedBy,         // optional - who submitted the evidence
        @JsonDeserialize(using = FlexibleOffsetDateTimeDeserializer.class)
        OffsetDateTime validFrom,   // optional - validity start (defaults to now)
        @JsonDeserialize(using = FlexibleOffsetDateTimeDeserializer.class)
        OffsetDateTime validUntil,  // optional - validity end
        String relatedEvidenceFields, // optional - related evidence fields for categorization
        String trackId,             // optional - link to track
        String documentId,          // optional - link to document
        String docVersionId         // optional - link to document version
) {}