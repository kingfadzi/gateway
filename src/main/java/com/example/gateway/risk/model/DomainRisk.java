package com.example.gateway.risk.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Domain-level risk aggregation entity.
 * Groups individual risk items by domain (e.g., Security, Integrity) per application.
 * Routes to appropriate Architecture Review Board (ARB) based on domain.
 */
@Data
@Entity
@Table(name = "domain_risk")
public class DomainRisk {

    @Id
    @Column(name = "domain_risk_id")
    private String domainRiskId;

    @Column(name = "app_id", nullable = false)
    private String appId;

    @Column(name = "domain", nullable = false, length = 100)
    private String domain;

    @Column(name = "derived_from", nullable = false, length = 100)
    private String derivedFrom;

    @Column(name = "arb", nullable = false, length = 100)
    private String arb;

    // Aggregated metadata
    @Column(name = "title", length = 500)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "total_items")
    private Integer totalItems = 0;

    @Column(name = "open_items")
    private Integer openItems = 0;

    @Column(name = "high_priority_items")
    private Integer highPriorityItems = 0;

    // Calculated priority/severity
    @Column(name = "overall_priority", length = 50)
    private String overallPriority;

    @Column(name = "overall_severity", length = 50)
    private String overallSeverity;

    @Column(name = "priority_score")
    private Integer priorityScore;

    // Status
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private DomainRiskStatus status = DomainRiskStatus.PENDING_ARB_REVIEW;

    // Assignment
    @Column(name = "assigned_arb", length = 100)
    private String assignedArb;

    @Column(name = "assigned_to", length = 255)
    private String assignedTo;

    @Column(name = "assigned_to_name", length = 255)
    private String assignedToName;

    @Column(name = "assigned_at")
    private OffsetDateTime assignedAt;

    // Lifecycle
    @Column(name = "opened_at", nullable = false)
    private OffsetDateTime openedAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "last_item_added_at")
    private OffsetDateTime lastItemAddedAt;

    // Audit
    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    // Relationships
    @OneToMany(mappedBy = "domainRisk", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<RiskItem> riskItems = new ArrayList<>();

    /**
     * Helper method to add a risk item to this domain risk
     */
    public void addRiskItem(RiskItem riskItem) {
        riskItems.add(riskItem);
        riskItem.setDomainRisk(this);
        riskItem.setDomainRiskId(this.domainRiskId);
    }

    /**
     * Helper method to remove a risk item from this domain risk
     */
    public void removeRiskItem(RiskItem riskItem) {
        riskItems.remove(riskItem);
        riskItem.setDomainRisk(null);
        riskItem.setDomainRiskId(null);
    }
}
