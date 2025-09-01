package com.example.onboarding.model.risk;

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

    private String title;

    private String hypothesis;

    private String condition;

    private String consequence;

    private String controlRefs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> attributes;

    private String severity;

    private String status;

    private String closureReason;

    @Column(nullable = false)
    private String raisedBy;

    private String owner;

    private OffsetDateTime openedAt;

    private OffsetDateTime closedAt;

    @Column(insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
