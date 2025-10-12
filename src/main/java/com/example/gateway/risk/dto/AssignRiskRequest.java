package com.example.gateway.risk.dto;

/**
 * Request DTO for assigning/reassigning a domain risk to an ARB.
 */
public record AssignRiskRequest(
        String assignedArb,
        String assignedBy,
        String assignmentReason  // Optional reason for reassignment
) {
    public boolean isValid() {
        return assignedArb != null && !assignedArb.isBlank() &&
               assignedBy != null && !assignedBy.isBlank();
    }
}
