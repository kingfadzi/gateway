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

        // References
        String appId,
        String fieldKey,
        String profileFieldId,
        String triggeringEvidenceId,
        String trackId,

        // Content
        String title,
        String description,

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

        // Snapshot
        Map<String, Object> policyRequirementSnapshot,

        // Audit
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
