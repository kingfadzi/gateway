package com.example.onboarding.model.risk;

import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Entity
@Table(name = "risk_story_evidence")
@IdClass(RiskStoryEvidenceId.class)
public class RiskStoryEvidence {

    @Id
    private String riskId;

    @Id
    private String evidenceId;

    private String submittedBy;

    @Column(insertable = false, updatable = false)
    private OffsetDateTime submittedAt;

    private String reviewStatus;

    private String reviewedBy;

    private OffsetDateTime reviewedAt;

    private String reviewComment;

    private String waiverReason;

    private OffsetDateTime waiverUntil;

    @Column(insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
