package com.example.onboarding.dto.evidence;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;

/**
 * Create/Upload evidence.
 * - Provide either profileFieldId OR a field key (aliased to "profileField").
 * - "uri" is required for link/JSON uploads; optional for file uploads (we'll synthesize one).
 * - Dates are ISO-8601 with offset, e.g. 2025-09-01T10:00:00Z
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateEvidenceRequest(
        String profileFieldId,   // preferred if you already have it
        @JsonAlias({"fieldKey", "profileFieldKey", "profileField"})
        String profileField,     // fallback: field key (e.g., "security.encryption_at_rest")
        String uri,              // required for link evidence; optional for files
        String type,             // e.g., "link", "file"
        String sourceSystem,     // e.g., "MANUAL", "CI", "SERVICE_NOW"
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
        OffsetDateTime validFrom,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
        OffsetDateTime validUntil,
        String note              // optional annotation
) {}
