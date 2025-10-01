package com.example.onboarding.evidence.dto;

import com.example.onboarding.evidence.model.EvidenceFieldLink;
import com.example.onboarding.evidence.model.EvidenceFieldLinkStatus;

import java.time.OffsetDateTime;

/**
 * Response for evidence-field link operations
 */
public record EvidenceFieldLinkResponse(
        String evidenceId,
        String profileFieldId,
        String appId,
        EvidenceFieldLinkStatus linkStatus,
        String linkedBy,
        OffsetDateTime linkedAt,
        String reviewedBy,
        OffsetDateTime reviewedAt,
        String reviewComment,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        
        // Optional risk information if auto-created
        String autoCreatedRiskId,
        String assignedSme,
        boolean riskWasCreated
) {
    
    public static EvidenceFieldLinkResponse fromEntity(EvidenceFieldLink link) {
        return new EvidenceFieldLinkResponse(
            link.getEvidenceId(),
            link.getProfileFieldId(),
            link.getAppId(),
            link.getLinkStatus(),
            link.getLinkedBy(),
            link.getLinkedAt(),
            link.getReviewedBy(),
            link.getReviewedAt(),
            link.getReviewComment(),
            link.getCreatedAt(),
            link.getUpdatedAt(),
            null,  // No risk info in basic entity conversion
            null,
            false
        );
    }
    
    public static EvidenceFieldLinkResponse fromEntityWithRisk(EvidenceFieldLink link, String riskId, String smeId) {
        return new EvidenceFieldLinkResponse(
            link.getEvidenceId(),
            link.getProfileFieldId(),
            link.getAppId(),
            link.getLinkStatus(),
            link.getLinkedBy(),
            link.getLinkedAt(),
            link.getReviewedBy(),
            link.getReviewedAt(),
            link.getReviewComment(),
            link.getCreatedAt(),
            link.getUpdatedAt(),
            riskId,
            smeId,
            true
        );
    }
}