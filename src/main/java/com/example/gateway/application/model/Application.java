package com.example.gateway.application.model;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "application")
public class Application {

    @Id
    @Column(name = "app_id", nullable = false)
    private String appId;

    @Column(nullable = false)
    private String scope = "application";

    @Column(name = "parent_app_id")
    private String parentAppId;

    @Column(name = "parent_app_name")
    private String parentAppName;

    private String name;
    @Column(name = "business_service_name")
    private String businessServiceName;

    @Column(name = "app_criticality_assessment")
    private String appCriticalityAssessment;

    @Column(name = "security_rating")
    private String securityRating;

    @Column(name = "confidentiality_rating")
    private String confidentialityRating;

    @Column(name = "integrity_rating")
    private String integrityRating;

    @Column(name = "availability_rating")
    private String availabilityRating;

    @Column(name = "resilience_rating")
    private String resilienceRating;

    @Column(name = "business_application_sys_id")
    private String businessApplicationSysId;

    @Column(name = "architecture_hosting")
    private String architectureHosting;

    @Column(name = "jira_backlog_id")
    private String jiraBacklogId;

    @Column(name = "lean_control_service_id")
    private String leanControlServiceId;

    @Column(name = "repo_id")
    private String repoId;

    @Column(name = "operational_status")
    private String operationalStatus;

    @Column(name = "transaction_cycle")
    private String transactionCycle;

    @Column(name = "transaction_cycle_id")
    private String transactionCycleId;

    @Column(name = "application_type")
    private String applicationType;

    @Column(name = "application_tier")
    private String applicationTier;

    @Column(name = "architecture_type")
    private String architectureType;

    @Column(name = "install_type")
    private String installType;

    @Column(name = "house_position")
    private String housePosition;

    @Column(name = "product_owner")
    private String productOwner;

    @Column(name = "product_owner_brid")
    private String productOwnerBrid;

    @Column(name = "system_architect")
    private String systemArchitect;

    @Column(name = "system_architect_brid")
    private String systemArchitectBrid;

    @Column(name = "onboarding_status", nullable = false)
    private String onboardingStatus = "pending";

    @Column(name = "owner_id")
    private String ownerId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    // If you use Builder, Lombok will handle builder() and all special constructors.
    // JPA requires a no-args constructor, which @NoArgsConstructor provides.
}
