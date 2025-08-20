package com.example.onboarding.application.dto;

/** Flat row returned by the source query (single row per appId) */
public record SourceRow(
        String appId,                  // child_app.correlation_id
        String businessServiceName,    // bs.service
        String applicationType,
        String applicationTier,
        String architectureType,
        String installType,
        String housePosition,
        String operationalStatus,
        String transactionCycle,

        // Ratings from service offering and CIA+S+R
        String appCriticality,         // so.app_criticality_assessment (A/B/C/D)
        String securityRating,         // A1/A2/B/C/D or A->A1
        String integrityRating,        // A/B/C/D
        String availabilityRating,     // A/B/C/D
        String resilienceRating        // "0".."4"
) { }
