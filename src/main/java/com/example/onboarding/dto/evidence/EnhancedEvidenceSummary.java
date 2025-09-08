package com.example.onboarding.dto.evidence;

import com.example.onboarding.model.EvidenceFieldLinkStatus;
import java.time.OffsetDateTime;

/**
 * Enhanced evidence summary that includes EvidenceFieldLink metadata
 * and document source information for showing evidence approval workflow status
 */
public record EnhancedEvidenceSummary(
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
        Integer documentLinkHealth  // Document health status (HTTP code)
) {}