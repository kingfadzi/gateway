package com.example.onboarding.dto.application;

public record PortfolioKpis(
    int compliant,
    int missingEvidence,
    int pendingReview,
    int riskBlocked
) {}
