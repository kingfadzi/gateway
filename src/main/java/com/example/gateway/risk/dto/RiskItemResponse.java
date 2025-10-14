package com.example.gateway.risk.dto;

import com.example.gateway.risk.model.RiskCreationType;
import com.example.gateway.risk.model.RiskItemStatus;
import com.example.gateway.risk.model.RiskPriority;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Response DTO for individual risk items.
 * Used by PO views to see evidence-level risks.
 */
public record RiskItemResponse(
        String riskItemId,
        String domainRiskId,
        String riskRatingDimension,
        String arb,

        // References
        String appId,
        String fieldKey,
        String profileFieldId,
        String triggeringEvidenceId,
        String trackId,

        // Content
        String title,
        String description,

        // Rich content (for manual SME-initiated risks)
        String hypothesis,
        String condition,
        String consequence,
        String controlRefs,

        // Priority & severity
        RiskPriority priority,
        String severity,
        Integer priorityScore,
        String evidenceStatus,

        // Status
        RiskItemStatus status,
        String resolution,
        String resolutionComment,

        // Lifecycle
        RiskCreationType creationType,
        String raisedBy,
        OffsetDateTime openedAt,
        OffsetDateTime resolvedAt,

        // Assignment (individual SME work assignment)
        String assignedTo,
        OffsetDateTime assignedAt,
        String assignedBy,

        // Snapshot
        Map<String, Object> policyRequirementSnapshot,

        // Audit
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
