package com.example.gateway.risk.dto;

import com.example.gateway.risk.model.DomainRiskStatus;

import java.time.OffsetDateTime;

/**
 * Response DTO for domain-level risk aggregations.
 * Used by ARB/SME views to see aggregated risks by domain.
 */
public record DomainRiskResponse(
        String domainRiskId,
        String appId,
        String domain,
        String derivedFrom,
        String arb,

        // Aggregated metadata
        String title,
        String description,
        Integer totalItems,
        Integer openItems,
        Integer highPriorityItems,

        // Priority & severity
        String overallPriority,
        String overallSeverity,
        Integer priorityScore,

        // Status
        DomainRiskStatus status,

        // Assignment
        String assignedArb,
        String assignedTo,
        String assignedToName,
        OffsetDateTime assignedAt,

        // Lifecycle
        OffsetDateTime openedAt,
        OffsetDateTime closedAt,
        OffsetDateTime lastItemAddedAt,

        // Audit
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
