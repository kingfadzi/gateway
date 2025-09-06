package com.example.onboarding.dto.application;

import java.util.List;

public record AppsResponse(
    List<AppSummaryResponse> apps,
    KpiSummary kpis,
    int totalCount,
    int filteredCount
) {}