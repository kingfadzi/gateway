package com.example.gateway.sme.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sme_queue")
public class SmeQueue {

    @Id
    @Column(name = "queue_id", nullable = false)
    private String queueId;

    @Column(name = "risk_id", nullable = false)
    private String riskId;

    @Column(name = "sme_id", nullable = false)
    private String smeId;

    @Column(name = "queue_status", nullable = false)
    private String queueStatus = "PENDING";

    @Column(name = "assigned_at", nullable = false)
    private OffsetDateTime assignedAt = OffsetDateTime.now();

    @Column(name = "picked_up_at")
    private OffsetDateTime pickedUpAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}