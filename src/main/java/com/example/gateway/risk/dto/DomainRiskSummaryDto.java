package com.example.gateway.risk.dto;

import com.example.gateway.risk.model.DomainRiskStatus;

import java.time.OffsetDateTime;

/**
 * Summary of a domain risk for application watchlist.
 * Includes assignment information.
 */
public record DomainRiskSummaryDto(
    String domainRiskId,
    String domain,
    DomainRiskStatus status,
    Integer priorityScore,
    Integer openItems,
    String assignedArb,
    String assignedTo,
    String assignedToName,
    OffsetDateTime assignedAt
) {
}
