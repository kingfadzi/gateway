package com.example.onboarding.risk.dto;

import com.example.onboarding.risk.model.RiskStatus;
import com.example.onboarding.risk.model.RiskCreationType;
import com.example.onboarding.risk.model.RiskStory;

import java.time.OffsetDateTime;
import java.util.Map;

public record RiskStoryResponse(
    // Core Identifiers
    String riskId,
    String appId,
    ApplicationDetails applicationDetails,
    String fieldKey,
    String profileId,
    String profileFieldId,
    String trackId,
    
    // Workflow Fields
    String triggeringEvidenceId,
    RiskCreationType creationType,
    String assignedSme,
    
    // Risk Content
    String title,
    String hypothesis,
    String condition,
    String consequence,
    String controlRefs,
    Map<String, Object> attributes,
    String severity,
    
    // Status & Ownership
    RiskStatus status,
    String closureReason,
    String raisedBy,
    String owner,
    
    // Timestamps
    OffsetDateTime openedAt,
    OffsetDateTime closedAt,
    OffsetDateTime assignedAt,
    OffsetDateTime reviewedAt,
    
    // Enhanced Features
    String reviewComment,
    Map<String, Object> policyRequirementSnapshot,
    
    // System Fields
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public static RiskStoryResponse fromModel(RiskStory model) {
        return fromModel(model, null);
    }
    
    public static RiskStoryResponse fromModel(RiskStory model, ApplicationDetails applicationDetails) {
        return new RiskStoryResponse(
            // Core Identifiers
            model.getRiskId(),
            model.getAppId(),
            applicationDetails,
            model.getFieldKey(),
            model.getProfileId(),
            model.getProfileFieldId(),
            model.getTrackId(),
            
            // Workflow Fields
            model.getTriggeringEvidenceId(),
            model.getCreationType(),
            model.getAssignedSme(),
            
            // Risk Content
            model.getTitle(),
            model.getHypothesis(),
            model.getCondition(),
            model.getConsequence(),
            model.getControlRefs(),
            model.getAttributes(),
            model.getSeverity(),
            
            // Status & Ownership
            model.getStatus(),
            model.getClosureReason(),
            model.getRaisedBy(),
            model.getOwner(),
            
            // Timestamps
            model.getOpenedAt(),
            model.getClosedAt(),
            model.getAssignedAt(),
            model.getReviewedAt(),
            
            // Enhanced Features
            model.getReviewComment(),
            model.getPolicyRequirementSnapshot(),
            
            // System Fields
            model.getCreatedAt(),
            model.getUpdatedAt()
        );
    }
    
    public record ApplicationDetails(
        String name,
        String scope,
        String parentAppId,
        String parentAppName,
        String businessServiceName,
        String appCriticalityAssessment,
        String securityRating,
        String confidentialityRating,
        String integrityRating,
        String availabilityRating,
        String resilienceRating,
        String businessApplicationSysId,
        String architectureHosting,
        String jiraBacklogId,
        String leanControlServiceId,
        String repoId,
        String operationalStatus,
        String transactionCycle,
        String transactionCycleId,
        String applicationType,
        String applicationTier,
        String architectureType,
        String installType,
        String housePosition,
        String productOwner,
        String productOwnerBrid,
        String systemArchitect,
        String systemArchitectBrid,
        String onboardingStatus,
        String ownerId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
    ) {}
}
