package com.example.gateway.risk.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.OffsetDateTime;

/**
 * Risk comment entity for discussion and collaboration on risk items.
 */
@Entity
@Table(name = "risk_comment")
@Data
public class RiskComment {

    @Id
    @Column(name = "comment_id")
    private String commentId;

    @Column(name = "risk_item_id", nullable = false)
    private String riskItemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "comment_type", nullable = false)
    private RiskCommentType commentType;

    @Column(name = "comment_text", nullable = false, columnDefinition = "TEXT")
    private String commentText;

    @Column(name = "commented_by", nullable = false)
    private String commentedBy;

    @Column(name = "commented_at", nullable = false)
    private OffsetDateTime commentedAt;

    @Column(name = "is_internal")
    private Boolean isInternal = false;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = OffsetDateTime.now();
        }
        if (commentedAt == null) {
            commentedAt = OffsetDateTime.now();
        }
        if (isInternal == null) {
            isInternal = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
