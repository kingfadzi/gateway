package com.example.gateway.risk.dto;

/**
 * Application metadata summary.
 * Source: Application entity.
 */
public record ApplicationSummary(
    String id,                           // Internal UUID
    String appId,                        // External ID (e.g., APM100001)
    String name,
    String appCriticalityAssessment,    // A/B/C/D - composite of CIA+S+R
    String transactionCycle,             // Business unit (e.g., "Retail", "Platform")
    String owner,                        // productOwner
    String ownerId                       // productOwnerBrid
) {
}
