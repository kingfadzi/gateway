package com.example.gateway.risk.dto;

import java.time.OffsetDateTime;

/**
 * Response DTO for assignment history audit trail.
 * Represents a single assignment change event.
 */
public record AssignmentHistoryResponse(
        String historyId,
        String assignedTo,       // Who received the assignment (null for unassignment)
        String assignedFrom,     // Previous assignee (null if first assignment)
        String assignedBy,       // Who performed the assignment action
        String assignmentType,   // "SELF_ASSIGN", "MANUAL_ASSIGN", "AUTO_ASSIGN", "UNASSIGN"
        String reason,           // Optional reason for the assignment
        OffsetDateTime assignedAt
) {}
