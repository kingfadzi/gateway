package com.example.gateway.risk.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * Assignment history entity for risk items.
 * Provides audit trail of who assigned what to whom and when.
 */
@Data
@Entity
@Table(name = "risk_item_assignment_history")
public class RiskItemAssignmentHistory {

    @Id
    @Column(name = "history_id")
    private String historyId;

    @Column(name = "risk_item_id", nullable = false)
    private String riskItemId;

    @Column(name = "assigned_to")
    private String assignedTo;  // Who received the assignment (null for unassignment)

    @Column(name = "assigned_from")
    private String assignedFrom;  // Previous assignee (null if first assignment)

    @Column(name = "assigned_by", nullable = false)
    private String assignedBy;  // Who performed the assignment action

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_type", nullable = false)
    private AssignmentType assignmentType;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;  // Optional reason for the assignment

    @Column(name = "assigned_at", nullable = false)
    private OffsetDateTime assignedAt;
}
