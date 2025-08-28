package com.example.onboarding.dto.document;

import java.time.OffsetDateTime;
import java.util.List;

public record DocumentResponse(
        String documentId,
        String appId,
        String title,
        String canonicalUrl,
        String sourceType,    // "gitlab" | "confluence" | "other"
        String owners,
        Integer linkHealth,   // HTTP status or health indicator
        List<String> relatedEvidenceFields,    // Field types this document can be used for
        DocumentVersionInfo latestVersion,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}