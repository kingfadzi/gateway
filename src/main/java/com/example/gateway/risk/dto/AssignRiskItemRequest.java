package com.example.gateway.risk.dto;

/**
 * Request DTO for assigning a risk item to a user (individual SME assignment).
 * Used for manual assignment and re-assignment workflows.
 */
public record AssignRiskItemRequest(
        String assignedTo,  // Email or user ID of the assignee
        String reason       // Optional: reason for assignment
) {
    public boolean isValid() {
        return assignedTo != null && !assignedTo.isBlank();
    }
}
