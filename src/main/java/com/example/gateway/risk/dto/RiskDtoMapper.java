package com.example.gateway.risk.dto;

import com.example.gateway.risk.model.DomainRisk;
import com.example.gateway.risk.model.RiskItem;
import com.example.gateway.risk.model.RiskComment;

/**
 * Utility class for mapping risk entities to DTOs.
 */
public class RiskDtoMapper {

    /**
     * Convert DomainRisk entity to DomainRiskResponse DTO
     */
    public static DomainRiskResponse toDomainRiskResponse(DomainRisk domainRisk) {
        if (domainRisk == null) {
            return null;
        }

        return new DomainRiskResponse(
                domainRisk.getDomainRiskId(),
                domainRisk.getAppId(),
                domainRisk.getRiskDimension(),
                domainRisk.getDerivedFrom(),
                domainRisk.getArb(),
                domainRisk.getTitle(),
                domainRisk.getDescription(),
                domainRisk.getTotalItems(),
                domainRisk.getOpenItems(),
                domainRisk.getHighPriorityItems(),
                domainRisk.getOverallPriority(),
                domainRisk.getOverallSeverity(),
                domainRisk.getPriorityScore(),
                domainRisk.getStatus(),
                domainRisk.getAssignedArb(),
                domainRisk.getAssignedTo(),
                domainRisk.getAssignedToName(),
                domainRisk.getAssignedAt(),
                domainRisk.getOpenedAt(),
                domainRisk.getClosedAt(),
                domainRisk.getLastItemAddedAt(),
                domainRisk.getCreatedAt(),
                domainRisk.getUpdatedAt()
        );
    }

    /**
     * Convert RiskItem entity to RiskItemResponse DTO
     */
    public static RiskItemResponse toRiskItemResponse(RiskItem riskItem) {
        if (riskItem == null) {
            return null;
        }

        return new RiskItemResponse(
                riskItem.getRiskItemId(),
                riskItem.getDomainRiskId(),
                riskItem.getAppId(),
                riskItem.getFieldKey(),
                riskItem.getProfileFieldId(),
                riskItem.getTriggeringEvidenceId(),
                riskItem.getTrackId(),
                riskItem.getTitle(),
                riskItem.getDescription(),
                riskItem.getHypothesis(),
                riskItem.getCondition(),
                riskItem.getConsequence(),
                riskItem.getControlRefs(),
                riskItem.getPriority(),
                riskItem.getSeverity(),
                riskItem.getPriorityScore(),
                riskItem.getEvidenceStatus(),
                riskItem.getStatus(),
                riskItem.getResolution(),
                riskItem.getResolutionComment(),
                riskItem.getCreationType(),
                riskItem.getRaisedBy(),
                riskItem.getOpenedAt(),
                riskItem.getResolvedAt(),
                riskItem.getAssignedTo(),
                riskItem.getAssignedAt(),
                riskItem.getAssignedBy(),
                riskItem.getPolicyRequirementSnapshot(),
                riskItem.getCreatedAt(),
                riskItem.getUpdatedAt()
        );
    }

    /**
     * Convert RiskComment entity to RiskCommentResponse DTO
     */
    public static RiskCommentResponse toRiskCommentResponse(RiskComment comment) {
        if (comment == null) {
            return null;
        }

        return new RiskCommentResponse(
                comment.getCommentId(),
                comment.getRiskItemId(),
                comment.getCommentType(),
                comment.getCommentText(),
                comment.getCommentedBy(),
                comment.getCommentedAt(),
                comment.getIsInternal(),
                comment.getCreatedAt(),
                comment.getUpdatedAt()
        );
    }
}
