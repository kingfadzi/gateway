package com.example.gateway.risk.dto;

import com.example.gateway.risk.model.RiskCommentType;

/**
 * Request DTO for creating a comment on a risk item.
 */
public record RiskCommentRequest(
        RiskCommentType commentType,
        String commentText,
        String commentedBy,
        Boolean isInternal  // Optional, defaults to false
) {
    public boolean isValid() {
        return commentType != null &&
               commentText != null && !commentText.isBlank() &&
               commentedBy != null && !commentedBy.isBlank();
    }

    public Boolean isInternal() {
        return isInternal != null ? isInternal : false;
    }
}
