package com.example.gateway.risk.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Individual risk item entity (evidence-level risk).
 * Belongs to a domain-level risk aggregation.
 * Represents what was previously a standalone RiskStory.
 */
@Data
@Entity
@Table(name = "risk_item")
public class RiskItem {

    @Id
    @Column(name = "risk_item_id")
    private String riskItemId;

    @Column(name = "domain_risk_id", nullable = false)
    private String domainRiskId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "domain_risk_id", insertable = false, updatable = false)
    private DomainRisk domainRisk;

    // References
    @Column(name = "app_id", nullable = false)
    private String appId;

    @Column(name = "field_key", nullable = false)
    private String fieldKey;

    @Column(name = "profile_field_id")
    private String profileFieldId;

    @Column(name = "triggering_evidence_id")
    private String triggeringEvidenceId;

    @Column(name = "track_id")
    private String trackId;

    // Content
    @Column(name = "title", length = 500)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // Priority & severity
    @Enumerated(EnumType.STRING)
    @Column(name = "priority", length = 50)
    private RiskPriority priority;

    @Column(name = "severity", length = 50)
    private String severity;

    @Column(name = "priority_score")
    private Integer priorityScore;

    @Column(name = "evidence_status", length = 50)
    private String evidenceStatus;

    // Status
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private RiskItemStatus status = RiskItemStatus.OPEN;

    @Column(name = "resolution", length = 50)
    private String resolution;

    @Column(name = "resolution_comment", columnDefinition = "TEXT")
    private String resolutionComment;

    // Lifecycle
    @Enumerated(EnumType.STRING)
    @Column(name = "creation_type", length = 50)
    private RiskCreationType creationType;

    @Column(name = "raised_by")
    private String raisedBy;

    @Column(name = "opened_at", nullable = false)
    private OffsetDateTime openedAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    // Snapshot
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "policy_requirement_snapshot", columnDefinition = "jsonb")
    private Map<String, Object> policyRequirementSnapshot;

    // Audit
    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
