package com.example.gateway.application.dto;

public record PortfolioKpis(
    int compliant,
    int missingEvidence,
    int pendingReview,
    int riskBlocked
) {}
