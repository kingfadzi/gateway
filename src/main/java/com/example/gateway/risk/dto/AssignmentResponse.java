package com.example.gateway.risk.dto;

import java.time.OffsetDateTime;

/**
 * Response DTO for risk item assignment operations.
 * Returned after self-assign, assign, or unassign actions.
 */
public record AssignmentResponse(
        String riskItemId,
        String assignedTo,       // Null for unassignment
        String assignedBy,
        OffsetDateTime assignedAt,
        String assignmentType,   // "SELF_ASSIGN", "MANUAL_ASSIGN", "UNASSIGN"
        String message
) {}
