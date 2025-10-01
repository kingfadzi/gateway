package com.example.gateway.application.dto;

public record KpiSummary(
    int compliant,
    int missingEvidence,
    int pendingReview,
    int riskBlocked
) {}