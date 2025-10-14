package com.example.gateway.risk.dto;

import com.example.gateway.risk.model.RiskItemStatus;

/**
 * Request DTO for updating risk item status.
 * Used when POs resolve or update risk items.
 */
public record RiskItemUpdateRequest(
        RiskItemStatus status,
        String resolution,
        String resolutionComment
) {
}
