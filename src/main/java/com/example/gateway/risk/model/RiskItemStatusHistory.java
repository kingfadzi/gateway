package com.example.gateway.risk.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Complete audit trail of all status changes for risk items.
 * Tracks who changed status, when, why, and provides context for each transition.
 */
@Data
@Entity
@Table(name = "risk_item_status_history")
public class RiskItemStatusHistory {

    @Id
    @Column(name = "history_id")
    private String historyId;

    @Column(name = "risk_item_id", nullable = false)
    private String riskItemId;

    // Status change
    @Column(name = "from_status", length = 50)
    private String fromStatus;  // null for initial creation

    @Column(name = "to_status", nullable = false, length = 50)
    private String toStatus;

    // Resolution details
    @Column(name = "resolution", length = 100)
    private String resolution;  // e.g., SME_APPROVED, SME_REJECTED, PO_SELF_ATTESTED

    @Column(name = "resolution_comment", columnDefinition = "TEXT")
    private String resolutionComment;

    // Actor information
    @Column(name = "changed_by", nullable = false)
    private String changedBy;  // User who made the change

    @Column(name = "actor_role", length = 50)
    private String actorRole;  // SME, PO, SYSTEM, ADMIN

    // Context
    @Column(name = "mitigation_plan", columnDefinition = "TEXT")
    private String mitigationPlan;  // For SME_APPROVED_WITH_MITIGATION

    @Column(name = "reassigned_to")
    private String reassignedTo;  // For reassignments

    // Timestamps
    @Column(name = "changed_at", nullable = false)
    private OffsetDateTime changedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    // Metadata
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;
}
