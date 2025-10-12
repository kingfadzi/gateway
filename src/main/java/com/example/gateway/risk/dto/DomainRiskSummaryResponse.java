package com.example.gateway.risk.dto;

/**
 * Summary DTO for ARB dashboard view.
 * Shows aggregate statistics across domains.
 */
public record DomainRiskSummaryResponse(
        String domain,
        Long count,
        Long totalOpenItems,
        Double avgPriorityScore
) {
}
