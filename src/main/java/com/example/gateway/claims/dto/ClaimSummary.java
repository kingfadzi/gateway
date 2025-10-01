package com.example.gateway.claims.dto;

import java.time.OffsetDateTime;

/**
 * Summary view of a control claim
 */
public record ClaimSummary(
        String claimId,
        String appId,
        String fieldKey,
        String method,
        String status,          // 'open'|'submitted'|'approved'|'rejected'
        String trackId,         // optional track reference
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}