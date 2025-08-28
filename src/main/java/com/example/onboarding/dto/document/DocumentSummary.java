package com.example.onboarding.dto.document;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Document info for list views
 */
public record DocumentSummary(
        String documentId,
        String appId,
        String title,
        String canonicalUrl,
        String sourceType,
        String owners,
        Integer linkHealth,
        List<String> tags,
        DocumentVersionInfo latestVersion,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}