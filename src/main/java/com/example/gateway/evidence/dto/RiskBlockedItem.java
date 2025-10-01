package com.example.gateway.evidence.dto;

import java.time.OffsetDateTime;

/**
 * Risk blocked item summary that includes full application context
 * for risk listing by KPI states
 */
public record RiskBlockedItem(
        String riskId,
        String appId,
        String fieldKey,
        String riskStatus,              // Risk status: PENDING_SME_REVIEW/UNDER_REVIEW/OPEN
        String assignedSme,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String triggeringEvidenceId,
        String title,
        String hypothesis,

        // Application context
        String appName,                 // Application name
        String productOwner,            // Product owner
        String applicationTier,         // Application tier (1, 2, 3, etc.)
        String architectureType,        // Architecture type (monolith, microservices, etc.)
        String installType,             // Install type (cloud, on-premise, hybrid)
        String appCriticality,          // App criticality assessment (A, B, C, etc.)

        // Profile field context
        String controlField,            // Profile field key for control context
        String derivedFrom,             // Used to derive domain
        String domainRating             // Domain rating value (e.g., A1, B, C)
) {
    /**
     * Derive domain from the derivedFrom field
     * e.g., "security_rating" -> "security"
     */
    public String getDomain() {
        if (derivedFrom == null) return null;
        if (derivedFrom.endsWith("_rating")) {
            return derivedFrom.substring(0, derivedFrom.lastIndexOf("_rating"));
        }
        return derivedFrom;
    }
}