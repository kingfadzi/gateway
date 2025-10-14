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
import java.util.Map;
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
     * Get applications with risk aggregations for ARB dashboard watchlist.
     * Supports three scopes: my-queue, my-domain, all-domains.
     *
     * GET /api/v1/domain-risks/arb/{arbName}/applications
     */
    @GetMapping("/arb/{arbName}/applications")
    public ResponseEntity<?> getApplicationsForArb(
            @PathVariable String arbName,
            @RequestParam String scope,
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "false") boolean includeRisks,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int pageSize) {

        log.info("GET /api/v1/domain-risks/arb/{}/applications - scope: {}, userId: {}, page: {}, size: {}",
                arbName, scope, userId, page, pageSize);

        try {
            // Validate page size
            if (pageSize < 1 || pageSize > 500) {
                return ResponseEntity.badRequest()
                        .body(Map.of(
                                "timestamp", java.time.OffsetDateTime.now().toString(),
                                "status", 400,
                                "error", "Bad Request",
                                "message", "pageSize must be between 1 and 500",
                                "path", "/api/v1/domain-risks/arb/" + arbName + "/applications"
                        ));
            }

            if (page < 0) {
                return ResponseEntity.badRequest()
                        .body(Map.of(
                                "timestamp", java.time.OffsetDateTime.now().toString(),
                                "status", 400,
                                "error", "Bad Request",
                                "message", "page must be >= 0",
                                "path", "/api/v1/domain-risks/arb/" + arbName + "/applications"
                        ));
            }

            ApplicationWatchlistResponse response = dashboardService.getApplicationsForArb(
                    arbName, scope, userId, includeRisks, page, pageSize);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.error("Validation error: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "timestamp", java.time.OffsetDateTime.now().toString(),
                            "status", 400,
                            "error", "Bad Request",
                            "message", e.getMessage(),
                            "path", "/api/v1/domain-risks/arb/" + arbName + "/applications"
                    ));
        } catch (Exception e) {
            log.error("Error getting applications for ARB: {}", arbName, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                            "timestamp", java.time.OffsetDateTime.now().toString(),
                            "status", 500,
                            "error", "Internal Server Error",
                            "message", "An error occurred while processing your request",
                            "path", "/api/v1/domain-risks/arb/" + arbName + "/applications"
                    ));
        }
    }

    /**
     * Get dashboard metrics for ARB HUD.
     * Supports three scopes: my-queue, my-domain, all-domains.
     *
     * GET /api/v1/domain-risks/arb/{arbName}/metrics
     */
    @GetMapping("/arb/{arbName}/metrics")
    public ResponseEntity<?> getMetricsForArb(
            @PathVariable String arbName,
            @RequestParam String scope,
            @RequestParam(required = false) String userId) {

        log.info("GET /api/v1/domain-risks/arb/{}/metrics - scope: {}, userId: {}",
                arbName, scope, userId);

        try {
            DashboardMetricsResponse response = dashboardService.getMetricsForArb(
                    arbName, scope, userId);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.error("Validation error: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "timestamp", java.time.OffsetDateTime.now().toString(),
                            "status", 400,
                            "error", "Bad Request",
                            "message", e.getMessage(),
                            "path", "/api/v1/domain-risks/arb/" + arbName + "/metrics"
                    ));
        } catch (Exception e) {
            log.error("Error getting metrics for ARB: {}", arbName, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                            "timestamp", java.time.OffsetDateTime.now().toString(),
                            "status", 500,
                            "error", "Internal Server Error",
                            "message", "An error occurred while processing your request",
                            "path", "/api/v1/domain-risks/arb/" + arbName + "/metrics"
                    ));
        }
    }

    /**
     * Get app-centric dashboard metrics for ARB HUD.
     * Focuses on counting applications at risk rather than individual risk items.
     * Supports three scopes: my-queue, my-domain, all-domains.
     *
     * GET /api/v1/domain-risks/arb/{arbName}/app-metrics
     */
    @GetMapping("/arb/{arbName}/app-metrics")
    public ResponseEntity<?> getAppCentricMetricsForArb(
            @PathVariable String arbName,
            @RequestParam String scope,
            @RequestParam(required = false) String userId) {

        log.info("GET /api/v1/domain-risks/arb/{}/app-metrics - scope: {}, userId: {}",
                arbName, scope, userId);

        try {
            ApplicationCentricMetricsResponse response = dashboardService.getAppCentricMetricsForArb(
                    arbName, scope, userId);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.error("Validation error: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "timestamp", java.time.OffsetDateTime.now().toString(),
                            "status", 400,
                            "error", "Bad Request",
                            "message", e.getMessage(),
                            "path", "/api/v1/domain-risks/arb/" + arbName + "/app-metrics"
                    ));
        } catch (Exception e) {
            log.error("Error getting app-centric metrics for ARB: {}", arbName, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                            "timestamp", java.time.OffsetDateTime.now().toString(),
                            "status", 500,
                            "error", "Internal Server Error",
                            "message", "An error occurred while processing your request",
                            "path", "/api/v1/domain-risks/arb/" + arbName + "/app-metrics"
                    ));
        }
    }

    /**
     * Assign application to ARB member.
     * Updates assigned_to and assigned_to_name for all domain risks belonging to the app+ARB.
     *
     * PATCH /api/v1/domain-risks/arb/{arbName}/applications/{appId}/assign
     */
    @PatchMapping("/arb/{arbName}/applications/{appId}/assign")
    public ResponseEntity<?> assignApplicationToMember(
            @PathVariable String arbName,
            @PathVariable String appId,
            @RequestBody AssignDomainRiskRequest request) {

        log.info("PATCH /api/v1/domain-risks/arb/{}/applications/{}/assign - assignedTo: {}, assignedToName: {}",
                arbName, appId, request.assignedTo(), request.assignedToName());

        try {
            // Validate request
            if (request.assignedTo() == null || request.assignedTo().isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of(
                                "timestamp", java.time.OffsetDateTime.now().toString(),
                                "status", 400,
                                "error", "Bad Request",
                                "message", "assignedTo is required",
                                "path", "/api/v1/domain-risks/arb/" + arbName + "/applications/" + appId + "/assign"
                        ));
            }

            // Assign application
            AssignDomainRiskResponse response = dashboardService.assignApplicationToArbMember(
                    arbName, appId, request.assignedTo(), request.assignedToName());

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.error("Validation error: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "timestamp", java.time.OffsetDateTime.now().toString(),
                            "status", 400,
                            "error", "Bad Request",
                            "message", e.getMessage(),
                            "path", "/api/v1/domain-risks/arb/" + arbName + "/applications/" + appId + "/assign"
                    ));
        } catch (Exception e) {
            log.error("Error assigning application {} to member: {}", appId, request.assignedTo(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                            "timestamp", java.time.OffsetDateTime.now().toString(),
                            "status", 500,
                            "error", "Internal Server Error",
                            "message", "An error occurred while processing your request",
                            "path", "/api/v1/domain-risks/arb/" + arbName + "/applications/" + appId + "/assign"
                    ));
        }
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
