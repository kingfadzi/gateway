package com.example.onboarding.dto.application;

/**
 * Authoritative application snapshot from source systems (SNOW views).
 * All fields required by the auto profile are included.
 */
public record SourceRow(
        String appId,
        String businessServiceName,
        String applicationName,

        String applicationType,
        String applicationTier,
        String architectureType,
        String installType,
        String housePosition,
        String operationalStatus,

        String transactionCycle,
        String transactionCycleId,            // from owning_transaction_cycle>id (aliased)

        String applicationProductOwner,
        String applicationProductOwnerBrid,
        String systemArchitect,
        String systemArchitectBrid,

        String businessApplicationSysId,      // child_app.business_application_sys_id
        String applicationParent,             // child_app.application_parent (name/label)
        String applicationParentId,           // child_app.application_parent_correlation_id
        String architectureHosting,           // child_app.architecture_hosting

        String appCriticality,
        String securityRating,
        String confidentialityRating,
        String integrityRating,
        String availabilityRating,
        String resilienceRating
) {}
