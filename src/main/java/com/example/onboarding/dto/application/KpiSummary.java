package com.example.onboarding.dto.application;

public record KpiSummary(
    int compliant,
    int missingEvidence,
    int pendingReview,
    int riskBlocked
) {}