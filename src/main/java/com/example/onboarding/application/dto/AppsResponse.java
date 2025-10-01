package com.example.onboarding.application.dto;

import java.util.List;

public record AppsResponse(
    List<AppSummaryResponse> apps,
    KpiSummary kpis,
    int totalCount,
    int filteredCount
) {}