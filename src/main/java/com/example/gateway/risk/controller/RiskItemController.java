package com.example.gateway.risk.controller;

import com.example.gateway.risk.dto.*;
import com.example.gateway.risk.model.*;
import com.example.gateway.risk.repository.RiskItemRepository;
import com.example.gateway.risk.repository.RiskCommentRepository;
import com.example.gateway.risk.service.DomainRiskAggregationService;
import com.example.gateway.profile.service.ProfileFieldRegistryService;
import com.example.gateway.profile.dto.ProfileFieldTypeInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
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
    private final RiskCommentRepository riskCommentRepository;
    private final DomainRiskAggregationService aggregationService;
    private final ProfileFieldRegistryService profileFieldRegistryService;

    public RiskItemController(
            RiskItemRepository riskItemRepository,
            RiskCommentRepository riskCommentRepository,
            DomainRiskAggregationService aggregationService,
            ProfileFieldRegistryService profileFieldRegistryService) {
        this.riskItemRepository = riskItemRepository;
        this.riskCommentRepository = riskCommentRepository;
        this.aggregationService = aggregationService;
        this.profileFieldRegistryService = profileFieldRegistryService;
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

    /**
     * Create a risk item manually (not triggered by evidence).
     * Used by ARB/SME to create risks outside the automatic flow.
     *
     * POST /api/v1/risk-items
     */
    @PostMapping
    public ResponseEntity<RiskItemResponse> createManualRisk(@RequestBody ManualRiskCreationRequest request) {
        log.info("POST /api/v1/risk-items - Manual risk creation for app: {}, field: {} by {}",
                request.appId(), request.fieldKey(), request.createdBy());

        // Validate request
        if (!request.isValid()) {
            return ResponseEntity.badRequest().build();
        }

        // Get derived_from for domain routing
        ProfileFieldTypeInfo fieldInfo = profileFieldRegistryService.getFieldTypeInfo(request.fieldKey())
                .orElseThrow(() -> new IllegalArgumentException("Unknown field key: " + request.fieldKey()));

        String derivedFrom = fieldInfo.derivedFrom();

        // Create risk item
        RiskItem riskItem = aggregationService.createManualRisk(
                request.appId(),
                request.fieldKey(),
                request.profileFieldId(),
                request.title(),
                request.description(),
                request.priority(),
                request.createdBy(),
                request.evidenceId(),
                derivedFrom
        );

        RiskItemResponse response = RiskDtoMapper.toRiskItemResponse(riskItem);

        log.info("Manually created risk item: {} for app: {}", riskItem.getRiskItemId(), request.appId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Add a comment to a risk item.
     *
     * POST /api/v1/risk-items/{riskItemId}/comments
     */
    @PostMapping("/{riskItemId}/comments")
    public ResponseEntity<RiskCommentResponse> addComment(
            @PathVariable String riskItemId,
            @RequestBody RiskCommentRequest request) {

        log.info("POST /api/v1/risk-items/{}/comments - type: {}, by: {}",
                riskItemId, request.commentType(), request.commentedBy());

        // Validate request
        if (!request.isValid()) {
            return ResponseEntity.badRequest().build();
        }

        // Verify risk item exists
        if (!riskItemRepository.existsById(riskItemId)) {
            return ResponseEntity.notFound().build();
        }

        // Create comment
        RiskComment comment = new RiskComment();
        comment.setCommentId("comment_" + UUID.randomUUID());
        comment.setRiskItemId(riskItemId);
        comment.setCommentType(request.commentType());
        comment.setCommentText(request.commentText());
        comment.setCommentedBy(request.commentedBy());
        comment.setIsInternal(request.isInternal());
        comment.setCommentedAt(OffsetDateTime.now());

        RiskComment saved = riskCommentRepository.save(comment);

        RiskCommentResponse response = RiskDtoMapper.toRiskCommentResponse(saved);

        log.info("Created comment {} on risk item {}", saved.getCommentId(), riskItemId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get all comments for a risk item.
     *
     * GET /api/v1/risk-items/{riskItemId}/comments
     */
    @GetMapping("/{riskItemId}/comments")
    public ResponseEntity<List<RiskCommentResponse>> getComments(
            @PathVariable String riskItemId,
            @RequestParam(required = false, defaultValue = "false") boolean includeInternal) {

        log.info("GET /api/v1/risk-items/{}/comments - includeInternal: {}", riskItemId, includeInternal);

        // Verify risk item exists
        if (!riskItemRepository.existsById(riskItemId)) {
            return ResponseEntity.notFound().build();
        }

        // Get comments
        List<RiskComment> comments = includeInternal
                ? riskCommentRepository.findByRiskItemIdOrderByCommentedAtDesc(riskItemId)
                : riskCommentRepository.findPublicCommentsByRiskItemId(riskItemId);

        List<RiskCommentResponse> responses = comments.stream()
                .map(RiskDtoMapper::toRiskCommentResponse)
                .collect(Collectors.toList());

        log.info("Found {} comments for risk item: {}", responses.size(), riskItemId);
        return ResponseEntity.ok(responses);
    }
}
