package com.example.gateway.risk.dto;

import java.util.List;

/**
 * Response DTO for bulk risk item update operations.
 * Provides detailed feedback on success/failure per item.
 */
public record BulkRiskItemUpdateResponse(
        int totalRequested,
        int successCount,
        int failureCount,
        List<String> successfulIds,
        List<BulkUpdateFailure> failures
) {
    /**
     * Individual failure details.
     */
    public record BulkUpdateFailure(
            String riskItemId,
            String reason
    ) {}
}
