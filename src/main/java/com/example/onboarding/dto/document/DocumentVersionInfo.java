package com.example.onboarding.dto.document;

import java.time.OffsetDateTime;

public record DocumentVersionInfo(
        String docVersionId,
        String versionId,      // commit hash for GitLab, version number for Confluence
        String urlAtVersion,   // URL pointing to specific version
        String author,         // commit author or page author
        OffsetDateTime sourceDate,  // Date from source system (commit date, page modified date)
        OffsetDateTime createdAt
) {}