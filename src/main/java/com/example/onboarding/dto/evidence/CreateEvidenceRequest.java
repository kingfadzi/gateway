package com.example.onboarding.dto.evidence;

// Accept either profileFieldId OR fieldKey (fallback). Always require uri.
public record CreateEvidenceRequest(
        String profileFieldId,   // preferred direct reference
        String fieldKey,         // fallback: resolve under latest profile for app
        String uri,              // required
        String type              // optional
) {}
