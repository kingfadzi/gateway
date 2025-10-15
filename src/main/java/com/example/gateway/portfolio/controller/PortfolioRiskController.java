package com.example.gateway.portfolio.controller;

import com.example.gateway.portfolio.dto.PortfolioRiskSummary;
import com.example.gateway.portfolio.service.PortfolioRiskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for portfolio-level risk views and dashboards.
 * Provides aggregated risk metrics for Product Owners.
 */
@RestController
@RequestMapping("/api/v1/portfolio")
@Tag(name = "Portfolio Risk Management", description = "Portfolio-level risk aggregations and dashboards")
public class PortfolioRiskController {

    private static final Logger log = LoggerFactory.getLogger(PortfolioRiskController.class);

    private final PortfolioRiskService portfolioRiskService;

    public PortfolioRiskController(PortfolioRiskService portfolioRiskService) {
        this.portfolioRiskService = portfolioRiskService;
    }

    /**
     * Get portfolio-level risk summary for a Product Owner.
     * Returns actionable risk metrics and critical app highlights.
     *
     * GET /api/v1/portfolio/risk-summary
     *
     * Metrics included:
     * - actionRequired: Risks needing PO action (AWAITING_REMEDIATION, IN_REMEDIATION)
     * - blockingCompliance: Critical/High priority risks blocking compliance
     * - missingEvidence: Risks with missing evidence
     * - pendingReview: Risks pending SME/PO review
     * - escalated: Escalated risks requiring attention
     * - recentWins: Risks resolved in last 7 days
     * - criticalApps: Top 10 apps with critical severity risks
     * - totalApps: Total apps in portfolio
     * - appsWithRisks: Apps with active risks
     */
    @Operation(
        summary = "Get portfolio risk summary",
        description = "Retrieves actionable risk metrics and critical apps for Product Owner dashboard"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Risk summary retrieved successfully"),
        @ApiResponse(responseCode = "400", description = "Missing or invalid product owner")
    })
    @GetMapping("/risk-summary")
    public ResponseEntity<PortfolioRiskSummary> getPortfolioRiskSummary(
            @Parameter(description = "Product Owner email/ID")
            @RequestHeader(value = "X-Product-Owner", required = false, defaultValue = "Owner Name") String productOwner) {

        log.info("GET /api/v1/portfolio/risk-summary - productOwner: {}", productOwner);

        if (productOwner == null || productOwner.isBlank()) {
            log.warn("Product owner not provided in request");
            return ResponseEntity.badRequest().build();
        }

        PortfolioRiskSummary summary = portfolioRiskService.getPortfolioRiskSummary(productOwner);

        log.info("Portfolio risk summary returned - actionRequired: {}, totalApps: {}",
                summary.actionRequired(), summary.totalApps());

        return ResponseEntity.ok(summary);
    }
}
