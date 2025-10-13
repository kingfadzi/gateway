package com.example.gateway.risk.dto;

/**
 * Request DTO for assigning a domain risk to an ARB member
 */
public record AssignDomainRiskRequest(
    String assignedTo,
    String assignedToName
) {
}
