package com.example.onboarding.risk.dto;

import com.example.onboarding.risk.model.RiskStoryEvidence;

import java.time.OffsetDateTime;

public record RiskStoryEvidenceResponse(
    String riskId,
    String evidenceId,
    String submittedBy,
    OffsetDateTime submittedAt,
    String reviewStatus,
    String reviewedBy,
    OffsetDateTime reviewedAt,
    String reviewComment,
    String waiverReason,
    OffsetDateTime waiverUntil,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public static RiskStoryEvidenceResponse fromModel(RiskStoryEvidence model) {
        return new RiskStoryEvidenceResponse(
            model.getRiskId(),
            model.getEvidenceId(),
            model.getSubmittedBy(),
            model.getSubmittedAt(),
            model.getReviewStatus(),
            model.getReviewedBy(),
            model.getReviewedAt(),
            model.getReviewComment(),
            model.getWaiverReason(),
            model.getWaiverUntil(),
            model.getCreatedAt(),
            model.getUpdatedAt()
        );
    }
}
