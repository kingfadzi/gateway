package com.example.gateway.risk.dto;

import java.time.OffsetDateTime;

/**
 * Summary of an app in a user's "My Queue" view.
 * Shows aggregated risk metrics for apps with assigned risks.
 */
public record MyQueueAppSummary(
        String appId,
        int totalRisks,           // Total risk items assigned to user for this app
        long openRisks,           // Risks with status = OPEN
        long inProgressRisks,     // Risks with status = IN_PROGRESS
        int highestPriority,      // Highest priority score among assigned risks
        OffsetDateTime lastAssignedAt  // Most recent assignment timestamp
) {}
