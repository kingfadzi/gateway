package com.example.onboarding.dto.risk;

import com.example.onboarding.model.risk.RiskStory;

import java.time.OffsetDateTime;
import java.util.Map;

public record RiskStoryResponse(
    String riskId,
    String appId,
    String fieldKey,
    String profileId,
    String profileFieldId,
    String trackId,
    String title,
    String hypothesis,
    String condition,
    String consequence,
    String controlRefs,
    Map<String, Object> attributes,
    String severity,
    String status,
    String closureReason,
    String raisedBy,
    String owner,
    OffsetDateTime openedAt,
    OffsetDateTime closedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public static RiskStoryResponse fromModel(RiskStory model) {
        return new RiskStoryResponse(
            model.getRiskId(),
            model.getAppId(),
            model.getFieldKey(),
            model.getProfileId(),
            model.getProfileFieldId(),
            model.getTrackId(),
            model.getTitle(),
            model.getHypothesis(),
            model.getCondition(),
            model.getConsequence(),
            model.getControlRefs(),
            model.getAttributes(),
            model.getSeverity(),
            model.getStatus(),
            model.getClosureReason(),
            model.getRaisedBy(),
            model.getOwner(),
            model.getOpenedAt(),
            model.getClosedAt(),
            model.getCreatedAt(),
            model.getUpdatedAt()
        );
    }
}
