package com.example.gateway.risk.dto;

import com.example.gateway.risk.model.RiskPriority;

/**
 * Request DTO for manually creating a risk item.
 */
public record ManualRiskCreationRequest(
        String appId,
        String fieldKey,
        String profileFieldId,
        String title,
        String description,
        RiskPriority priority,
        String createdBy,
        String evidenceId  // Optional - can be null if not triggered by evidence
) {
    public boolean isValid() {
        return appId != null && !appId.isBlank() &&
               fieldKey != null && !fieldKey.isBlank() &&
               title != null && !title.isBlank() &&
               description != null && !description.isBlank() &&
               priority != null &&
               createdBy != null && !createdBy.isBlank();
    }
}
