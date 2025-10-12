package com.example.gateway.risk.dto;

import com.example.gateway.risk.model.RiskCommentType;
import java.time.OffsetDateTime;

/**
 * Response DTO for risk comments.
 */
public record RiskCommentResponse(
        String commentId,
        String riskItemId,
        RiskCommentType commentType,
        String commentText,
        String commentedBy,
        OffsetDateTime commentedAt,
        Boolean isInternal,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
