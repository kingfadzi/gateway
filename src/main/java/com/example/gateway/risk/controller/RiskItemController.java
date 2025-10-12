package com.example.gateway.risk.controller;

import com.example.gateway.risk.dto.RiskDtoMapper;
import com.example.gateway.risk.dto.RiskItemResponse;
import com.example.gateway.risk.dto.RiskItemUpdateRequest;
import com.example.gateway.risk.model.RiskItem;
import com.example.gateway.risk.model.RiskItemStatus;
import com.example.gateway.risk.repository.RiskItemRepository;
import com.example.gateway.risk.service.DomainRiskAggregationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for individual risk items.
 * Provides PO views of evidence-level risks.
 */
@RestController
@RequestMapping("/api/v1/risk-items")
public class RiskItemController {

    private static final Logger log = LoggerFactory.getLogger(RiskItemController.class);

    private final RiskItemRepository riskItemRepository;
    private final DomainRiskAggregationService aggregationService;

    public RiskItemController(
            RiskItemRepository riskItemRepository,
            DomainRiskAggregationService aggregationService) {
        this.riskItemRepository = riskItemRepository;
        this.aggregationService = aggregationService;
    }

    /**
     * Get all risk items for an application, prioritized by score.
     * Used by PO workbench view.
     *
     * GET /api/v1/risk-items/app/{appId}
     */
    @GetMapping("/app/{appId}")
    public ResponseEntity<List<RiskItemResponse>> getRiskItemsForApp(@PathVariable String appId) {
        log.info("GET /api/v1/risk-items/app/{}", appId);

        List<RiskItem> riskItems = aggregationService.getRiskItemsForApp(appId);

        List<RiskItemResponse> responses = riskItems.stream()
                .map(RiskDtoMapper::toRiskItemResponse)
                .collect(Collectors.toList());

        log.info("Found {} risk items for app: {}", responses.size(), appId);
        return ResponseEntity.ok(responses);
    }

    /**
     * Get risk items for an app filtered by status.
     *
     * GET /api/v1/risk-items/app/{appId}/status/{status}
     */
    @GetMapping("/app/{appId}/status/{status}")
    public ResponseEntity<List<RiskItemResponse>> getRiskItemsByStatus(
            @PathVariable String appId,
            @PathVariable RiskItemStatus status) {

        log.info("GET /api/v1/risk-items/app/{}/status/{}", appId, status);

        List<RiskItem> riskItems = riskItemRepository.findByAppIdAndStatusPrioritized(appId, status);

        List<RiskItemResponse> responses = riskItems.stream()
                .map(RiskDtoMapper::toRiskItemResponse)
                .collect(Collectors.toList());

        log.info("Found {} risk items for app: {} with status: {}", responses.size(), appId, status);
        return ResponseEntity.ok(responses);
    }

    /**
     * Get a specific risk item by ID.
     *
     * GET /api/v1/risk-items/{riskItemId}
     */
    @GetMapping("/{riskItemId}")
    public ResponseEntity<RiskItemResponse> getRiskItem(@PathVariable String riskItemId) {
        log.info("GET /api/v1/risk-items/{}", riskItemId);

        return riskItemRepository.findById(riskItemId)
                .map(RiskDtoMapper::toRiskItemResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Update risk item status.
     * Used when PO resolves or updates a risk item.
     *
     * PATCH /api/v1/risk-items/{riskItemId}/status
     */
    @PatchMapping("/{riskItemId}/status")
    public ResponseEntity<RiskItemResponse> updateRiskItemStatus(
            @PathVariable String riskItemId,
            @RequestBody RiskItemUpdateRequest request) {

        log.info("PATCH /api/v1/risk-items/{}/status - status: {}, resolution: {}",
                riskItemId, request.status(), request.resolution());

        // Verify risk item exists
        if (!riskItemRepository.existsById(riskItemId)) {
            return ResponseEntity.notFound().build();
        }

        // Update status (this also recalculates domain risk aggregations)
        aggregationService.updateRiskItemStatus(
                riskItemId,
                request.status(),
                request.resolution(),
                request.resolutionComment()
        );

        // Fetch updated risk item
        RiskItem updated = riskItemRepository.findById(riskItemId)
                .orElseThrow(() -> new IllegalStateException("Risk item not found after update"));

        RiskItemResponse response = RiskDtoMapper.toRiskItemResponse(updated);

        log.info("Updated risk item {} to status: {}", riskItemId, request.status());
        return ResponseEntity.ok(response);
    }

    /**
     * Get all risk items by field key.
     * Used for field-specific analysis.
     *
     * GET /api/v1/risk-items/field/{fieldKey}
     */
    @GetMapping("/field/{fieldKey}")
    public ResponseEntity<List<RiskItemResponse>> getRiskItemsByField(@PathVariable String fieldKey) {
        log.info("GET /api/v1/risk-items/field/{}", fieldKey);

        List<RiskItem> riskItems = riskItemRepository.findByFieldKey(fieldKey);

        List<RiskItemResponse> responses = riskItems.stream()
                .map(RiskDtoMapper::toRiskItemResponse)
                .collect(Collectors.toList());

        log.info("Found {} risk items for field: {}", responses.size(), fieldKey);
        return ResponseEntity.ok(responses);
    }

    /**
     * Get all risk items triggered by specific evidence.
     * Used to see which risks were created from a specific evidence submission.
     *
     * GET /api/v1/risk-items/evidence/{evidenceId}
     */
    @GetMapping("/evidence/{evidenceId}")
    public ResponseEntity<List<RiskItemResponse>> getRiskItemsByEvidence(@PathVariable String evidenceId) {
        log.info("GET /api/v1/risk-items/evidence/{}", evidenceId);

        List<RiskItem> riskItems = riskItemRepository.findByTriggeringEvidenceId(evidenceId);

        List<RiskItemResponse> responses = riskItems.stream()
                .map(RiskDtoMapper::toRiskItemResponse)
                .collect(Collectors.toList());

        log.info("Found {} risk items for evidence: {}", responses.size(), evidenceId);
        return ResponseEntity.ok(responses);
    }
}
