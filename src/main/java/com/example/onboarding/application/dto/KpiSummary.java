package com.example.onboarding.application.dto;

public record KpiSummary(
    int compliant,
    int missingEvidence,
    int pendingReview,
    int riskBlocked
) {}