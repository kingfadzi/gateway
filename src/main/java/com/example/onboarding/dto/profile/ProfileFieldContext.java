package com.example.onboarding.dto.profile;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Comprehensive context information for a specific profile field
 */
public record ProfileFieldContext(
        String fieldId,
        String fieldKey,
        String label,
        Object value,
        String derivedFrom,
        String domain,
        String sourceSystem,
        String sourceRef,
        String assurance,
        Object policyRequirement,
        List<EvidenceGraphPayload> evidence,
        List<RiskGraphPayload> risks,
        OffsetDateTime updatedAt,
        int evidenceCount
) {}