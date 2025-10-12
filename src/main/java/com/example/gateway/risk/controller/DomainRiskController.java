package com.example.gateway.risk.controller;

import com.example.gateway.risk.dto.*;
import com.example.gateway.risk.model.DomainRisk;
import com.example.gateway.risk.model.DomainRiskStatus;
import com.example.gateway.risk.model.RiskItem;
import com.example.gateway.risk.repository.DomainRiskRepository;
import com.example.gateway.risk.service.ArbDashboardService;
import com.example.gateway.risk.service.DomainRiskAggregationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for domain-level risk aggregations.
 * Provides ARB/SME views of risks grouped by domain.
 */
@RestController
@RequestMapping("/api/v1/domain-risks")
public class DomainRiskController {

    private static final Logger log = LoggerFactory.getLogger(DomainRiskController.class);

    private final DomainRiskRepository domainRiskRepository;
    private final DomainRiskAggregationService aggregationService;
    private final ArbDashboardService dashboardService;

    public DomainRiskController(
            DomainRiskRepository domainRiskRepository,
            DomainRiskAggregationService aggregationService,
            ArbDashboardService dashboardService) {
        this.domainRiskRepository = domainRiskRepository;
        this.aggregationService = aggregationService;
        this.dashboardService = dashboardService;
    }

    /**
     * Get all domain risks for a specific ARB.
     * Used by ARB workbench view.
     *
     * GET /api/v1/domain-risks/arb/{arbName}?status=PENDING_ARB_REVIEW,UNDER_ARB_REVIEW
     */
    @GetMapping("/arb/{arbName}")
    public ResponseEntity<List<DomainRiskResponse>> getDomainRisksForArb(
            @PathVariable String arbName,
            @RequestParam(required = false, defaultValue = "PENDING_ARB_REVIEW,UNDER_ARB_REVIEW,AWAITING_REMEDIATION,IN_PROGRESS") String status) {

        log.info("GET /api/v1/domain-risks/arb/{} with status={}", arbName, status);

        // Parse status parameter
        List<DomainRiskStatus> statuses = parseStatuses(status);

        // Get domain risks for ARB
        List<DomainRisk> domainRisks = aggregationService.getDomainRisksForArb(arbName, statuses);

        // Convert to DTOs
        List<DomainRiskResponse> responses = domainRisks.stream()
                .map(RiskDtoMapper::toDomainRiskResponse)
                .collect(Collectors.toList());

        log.info("Found {} domain risks for ARB: {}", responses.size(), arbName);
        return ResponseEntity.ok(responses);
    }

    /**
     * Get domain risk summary statistics for ARB dashboard.
     *
     * GET /api/v1/domain-risks/arb/{arbName}/summary
     */
    @GetMapping("/arb/{arbName}/summary")
    public ResponseEntity<List<DomainRiskSummaryResponse>> getArbSummary(
            @PathVariable String arbName,
            @RequestParam(required = false, defaultValue = "PENDING_ARB_REVIEW,UNDER_ARB_REVIEW,AWAITING_REMEDIATION,IN_PROGRESS") String status) {

        log.info("GET /api/v1/domain-risks/arb/{}/summary with status={}", arbName, status);

        // Parse status parameter
        List<DomainRiskStatus> statuses = parseStatuses(status);

        // Get summary from repository
        List<Object[]> summaryData = domainRiskRepository.getDomainSummaryForArb(arbName, statuses);

        // Convert to DTOs
        List<DomainRiskSummaryResponse> responses = summaryData.stream()
                .map(row -> new DomainRiskSummaryResponse(
                        (String) row[0],      // domain
                        (Long) row[1],        // count
                        (Long) row[2],        // totalOpenItems
                        (Double) row[3]       // avgPriorityScore
                ))
                .collect(Collectors.toList());

        log.info("Generated summary for ARB: {} with {} domains", arbName, responses.size());
        return ResponseEntity.ok(responses);
    }

    /**
     * Get comprehensive dashboard metrics for ARB.
     * Provides all metrics needed for a dashboard visualization including:
     * - Overview statistics
     * - Domain breakdown
     * - Top applications
     * - Status distribution
     * - Priority distribution
     * - Recent activity
     *
     * GET /api/v1/domain-risks/arb/{arbName}/dashboard
     */
    @GetMapping("/arb/{arbName}/dashboard")
    public ResponseEntity<ArbDashboardResponse> getArbDashboard(
            @PathVariable String arbName,
            @RequestParam(required = false, defaultValue = "PENDING_ARB_REVIEW,UNDER_ARB_REVIEW,AWAITING_REMEDIATION,IN_PROGRESS") String status) {

        log.info("GET /api/v1/domain-risks/arb/{}/dashboard with status={}", arbName, status);

        // Parse status parameter
        List<DomainRiskStatus> statuses = parseStatuses(status);

        // Build comprehensive dashboard
        ArbDashboardResponse dashboard = dashboardService.buildDashboard(arbName, statuses);

        log.info("Generated dashboard for ARB: {} with {} domain risks",
                arbName, dashboard.overview().totalDomainRisks());
        return ResponseEntity.ok(dashboard);
    }

    /**
     * Get a specific domain risk by ID.
     *
     * GET /api/v1/domain-risks/{domainRiskId}
     */
    @GetMapping("/{domainRiskId}")
    public ResponseEntity<DomainRiskResponse> getDomainRisk(@PathVariable String domainRiskId) {
        log.info("GET /api/v1/domain-risks/{}", domainRiskId);

        return domainRiskRepository.findById(domainRiskId)
                .map(RiskDtoMapper::toDomainRiskResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get all risk items for a specific domain risk.
     * Used to drill down from domain-level to evidence-level.
     *
     * GET /api/v1/domain-risks/{domainRiskId}/items
     */
    @GetMapping("/{domainRiskId}/items")
    public ResponseEntity<List<RiskItemResponse>> getRiskItemsForDomain(@PathVariable String domainRiskId) {
        log.info("GET /api/v1/domain-risks/{}/items", domainRiskId);

        // Verify domain risk exists
        if (!domainRiskRepository.existsById(domainRiskId)) {
            return ResponseEntity.notFound().build();
        }

        // Get risk items
        List<RiskItem> riskItems = aggregationService.getRiskItemsForDomain(domainRiskId);

        // Convert to DTOs
        List<RiskItemResponse> responses = riskItems.stream()
                .map(RiskDtoMapper::toRiskItemResponse)
                .collect(Collectors.toList());

        log.info("Found {} risk items for domain risk: {}", responses.size(), domainRiskId);
        return ResponseEntity.ok(responses);
    }

    /**
     * Get all domain risks for an application.
     * Used to see all domain-level risks for a specific app.
     *
     * GET /api/v1/domain-risks/app/{appId}
     */
    @GetMapping("/app/{appId}")
    public ResponseEntity<List<DomainRiskResponse>> getDomainRisksForApp(@PathVariable String appId) {
        log.info("GET /api/v1/domain-risks/app/{}", appId);

        List<DomainRisk> domainRisks = domainRiskRepository.findByAppId(appId);

        List<DomainRiskResponse> responses = domainRisks.stream()
                .map(RiskDtoMapper::toDomainRiskResponse)
                .collect(Collectors.toList());

        log.info("Found {} domain risks for app: {}", responses.size(), appId);
        return ResponseEntity.ok(responses);
    }

    /**
     * Reassign a domain risk to a different ARB.
     * Used for workload balancing or expertise matching.
     *
     * PATCH /api/v1/domain-risks/{domainRiskId}/assign
     */
    @PatchMapping("/{domainRiskId}/assign")
    public ResponseEntity<DomainRiskResponse> reassignDomainRisk(
            @PathVariable String domainRiskId,
            @RequestBody AssignRiskRequest request) {

        log.info("PATCH /api/v1/domain-risks/{}/assign - new ARB: {}, by: {}",
                domainRiskId, request.assignedArb(), request.assignedBy());

        // Validate request
        if (!request.isValid()) {
            return ResponseEntity.badRequest().build();
        }

        // Verify domain risk exists
        if (!domainRiskRepository.existsById(domainRiskId)) {
            return ResponseEntity.notFound().build();
        }

        // Reassign
        DomainRisk updated = aggregationService.reassignDomainRisk(
                domainRiskId,
                request.assignedArb(),
                request.assignedBy()
        );

        DomainRiskResponse response = RiskDtoMapper.toDomainRiskResponse(updated);

        log.info("Reassigned domain risk {} to ARB: {}", domainRiskId, request.assignedArb());
        return ResponseEntity.ok(response);
    }

    /**
     * Helper method to parse comma-separated status parameter
     */
    private List<DomainRiskStatus> parseStatuses(String statusParam) {
        return List.of(statusParam.split(","))
                .stream()
                .map(String::trim)
                .map(DomainRiskStatus::valueOf)
                .collect(Collectors.toList());
    }
}
