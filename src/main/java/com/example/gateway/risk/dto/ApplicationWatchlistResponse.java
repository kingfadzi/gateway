package com.example.gateway.risk.dto;

import java.util.List;

/**
 * Response for GET /api/v1/domain-risks/arb/{arbName}/applications
 * Returns applications with risk aggregations for ARB dashboard watchlist.
 */
public record ApplicationWatchlistResponse(
    String scope,              // my-queue, my-domain, all-domains
    String arbName,
    String userId,             // Only for my-queue scope
    Integer totalCount,
    Integer page,
    Integer pageSize,
    List<ApplicationWithRisks> applications
) {
}
