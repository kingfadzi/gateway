package com.example.onboarding.application.dto;

public record PortfolioKpis(
    int compliant,
    int missingEvidence,
    int pendingReview,
    int riskBlocked
) {}
