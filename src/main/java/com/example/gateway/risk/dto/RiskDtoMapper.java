package com.example.gateway.risk.dto;

import com.example.gateway.risk.model.DomainRisk;
import com.example.gateway.risk.model.RiskItem;

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
                domainRisk.getDomain(),
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
                riskItem.getPolicyRequirementSnapshot(),
                riskItem.getCreatedAt(),
                riskItem.getUpdatedAt()
        );
    }
}
