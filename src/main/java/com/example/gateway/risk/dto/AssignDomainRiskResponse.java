package com.example.gateway.risk.dto;

import java.time.Instant;

/**
 * Response DTO for domain risk assignment operation
 */
public record AssignDomainRiskResponse(
    String appId,
    String name,
    String assignedTo,
    String assignedToName,
    Instant assignedAt,
    String message
) {
}
