package com.example.gateway.risk.dto;

import com.example.gateway.risk.model.RiskItemStatus;

import java.util.List;

/**
 * Request DTO for bulk updating multiple risk items.
 * Supports applying same action to multiple risks at once.
 */
public record BulkRiskItemUpdateRequest(
        List<String> riskItemIds,
        RiskItemStatus status,
        String resolution,
        String resolutionComment
) {
    /**
     * Validate request has required fields.
     */
    public boolean isValid() {
        return riskItemIds != null && !riskItemIds.isEmpty() && status != null;
    }
}
