package com.example.onboarding.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "evidence_field_link")
@IdClass(EvidenceFieldLinkId.class)
public class EvidenceFieldLink {

    @Id
    @Column(name = "evidence_id", nullable = false)
    private String evidenceId;

    @Id
    @Column(name = "profile_field_id", nullable = false)
    private String profileFieldId;

    @Column(name = "app_id", nullable = false)
    private String appId;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_status", nullable = false, columnDefinition = "evidence_field_link_status")
    private EvidenceFieldLinkStatus linkStatus = EvidenceFieldLinkStatus.PENDING_REVIEW;

    @Column(name = "linked_by", nullable = false)
    private String linkedBy;

    @Column(name = "linked_at", nullable = false)
    private OffsetDateTime linkedAt = OffsetDateTime.now();

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "review_comment")
    private String reviewComment;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}