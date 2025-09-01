package com.example.onboarding.model.risk;

import com.example.onboarding.model.RiskStatus;
import com.example.onboarding.model.RiskCreationType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
@Entity
@Table(name = "risk_story")
public class RiskStory {

    @Id
    private String riskId;

    @Column(nullable = false)
    private String appId;

    @Column(nullable = false)
    private String fieldKey;

    private String profileId;

    private String profileFieldId;

    private String trackId;

    // New fields for enhanced workflow
    private String triggeringEvidenceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RiskCreationType creationType = RiskCreationType.MANUAL_SME_INITIATED;

    private String assignedSme;

    private String title;

    private String hypothesis;

    private String condition;

    private String consequence;

    private String controlRefs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> attributes;

    private String severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RiskStatus status = RiskStatus.PENDING_SME_REVIEW;

    private String closureReason;

    @Column(nullable = false)
    private String raisedBy;

    private String owner;

    private OffsetDateTime openedAt;

    private OffsetDateTime closedAt;

    // Enhanced timestamps
    private OffsetDateTime assignedAt;

    private OffsetDateTime reviewedAt;

    private String reviewComment;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> policyRequirementSnapshot;

    @Column(insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
