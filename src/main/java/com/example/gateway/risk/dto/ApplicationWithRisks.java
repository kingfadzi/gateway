package com.example.gateway.risk.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Application with risk aggregations for ARB dashboard watchlist.
 * Combines application metadata with risk calculations.
 */
public record ApplicationWithRisks(
    // Application metadata
    String id,
    String appId,
    String name,
    String appCriticalityAssessment,
    String transactionCycle,
    String owner,
    String ownerId,

    // Risk aggregations
    Integer aggregatedRiskScore,         // MAX priority score across domain risks
    Integer totalOpenItems,              // SUM of open items
    RiskBreakdown riskBreakdown,         // Count by priority (OPEN/IN_PROGRESS only)
    List<String> domains,                // Domains with active risks
    Boolean hasAssignedRisks,            // Any risk assigned to requesting user
    OffsetDateTime lastActivityDate,     // Most recent activity timestamp

    // Domain risks summary
    List<DomainRiskSummaryDto> domainRisks,

    // Optional: detailed risk items (when includeRisks=true)
    List<RiskItemResponse> risks,

    // NEW: Risks assigned to specific user (populated when userId provided)
    RiskBreakdown assignedToMeBreakdown  // Count by priority for user's assigned items
) {
}
