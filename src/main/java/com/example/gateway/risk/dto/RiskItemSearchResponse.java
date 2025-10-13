package com.example.gateway.risk.dto;

import java.util.List;

/**
 * Paginated response for risk item search.
 * Includes pagination metadata along with search results.
 */
public record RiskItemSearchResponse(
        List<RiskItemResponse> items,
        int currentPage,
        int pageSize,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
}
