package com.example.onboarding.dto.evidence;

import com.example.onboarding.model.EvidenceFieldLinkStatus;
import java.time.OffsetDateTime;

/**
 * KPI-focused evidence summary that includes full application context
 * for evidence listing by KPI states (compliant, pending, etc.)
 */
public record KpiEvidenceSummary(
        String evidenceId,
        String appId,
        String profileFieldId,
        String claimId,
        String uri,
        String type,
        String status,              // Evidence status: active/superseded/revoked
        String submittedBy,
        OffsetDateTime validFrom,
        OffsetDateTime validUntil,
        String trackId,
        String documentId,
        String docVersionId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,

        // EvidenceFieldLink metadata
        EvidenceFieldLinkStatus linkStatus,    // ATTACHED/PENDING_PO_REVIEW/PENDING_SME_REVIEW/APPROVED/USER_ATTESTED/REJECTED
        String linkedBy,
        OffsetDateTime linkedAt,
        String reviewedBy,
        OffsetDateTime reviewedAt,
        String reviewComment,

        // Document source information
        String documentTitle,       // Document title
        String documentSourceType,  // confluence, gitlab, platform, manual
        String documentOwners,      // Document owners/authors
        Integer documentLinkHealth, // Document health status (HTTP code)

        // Profile field context
        String fieldKey,            // Profile field key
        String derivedFrom,         // Used to derive domain
        String domainRating,        // Domain rating value (e.g., A1, B, C)

        // Application context (requested by frontend)
        String appName,             // Application name
        String productOwner,        // Product owner
        String applicationTier,     // Application tier (1, 2, 3, etc.)
        String architectureType,    // Architecture type (monolith, microservices, etc.)
        String installType,         // Install type (cloud, on-premise, hybrid)
        String appCriticality,      // App criticality assessment (A, B, C, etc.)

        // Profile version
        Integer profileVersion      // Profile version number
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