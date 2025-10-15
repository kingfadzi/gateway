package com.example.gateway.risk.controller;

import com.example.gateway.risk.dto.*;
import com.example.gateway.risk.model.*;
import com.example.gateway.risk.repository.RiskItemRepository;
import com.example.gateway.risk.repository.RiskCommentRepository;
import com.example.gateway.risk.service.DomainRiskAggregationService;
import com.example.gateway.risk.service.RiskAssignmentService;
import com.example.gateway.risk.service.StatusHistoryService;
import com.example.gateway.profile.service.ProfileFieldRegistryService;
import com.example.gateway.profile.dto.ProfileFieldTypeInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
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
    private final RiskAssignmentService assignmentService;
    private final ProfileFieldRegistryService profileFieldRegistryService;
    private final StatusHistoryService statusHistoryService;

    public RiskItemController(
            RiskItemRepository riskItemRepository,
            RiskCommentRepository riskCommentRepository,
            DomainRiskAggregationService aggregationService,
            RiskAssignmentService assignmentService,
            ProfileFieldRegistryService profileFieldRegistryService,
            StatusHistoryService statusHistoryService) {
        this.riskItemRepository = riskItemRepository;
        this.riskCommentRepository = riskCommentRepository;
        this.aggregationService = aggregationService;
        this.assignmentService = assignmentService;
        this.profileFieldRegistryService = profileFieldRegistryService;
        this.statusHistoryService = statusHistoryService;
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
     * Bulk update multiple risk items with the same status change.
     * Useful for resolving/closing multiple related risks at once.
     *
     * POST /api/v1/risk-items/bulk-update
     *
     * Request body:
     * {
     *   "riskItemIds": ["item_uuid1", "item_uuid2", ...],
     *   "status": "RESOLVED",
     *   "resolution": "Fixed in release v2.3",
     *   "resolutionComment": "All items addressed in latest deployment"
     * }
     *
     * Response includes:
     * - totalRequested: Number of IDs in request
     * - successCount: Number successfully updated
     * - failureCount: Number that failed
     * - successfulIds: List of successfully updated IDs
     * - failures: List of failures with reasons
     */
    @PostMapping("/bulk-update")
    public ResponseEntity<BulkRiskItemUpdateResponse> bulkUpdateRiskItems(
            @RequestBody BulkRiskItemUpdateRequest request) {

        log.info("POST /api/v1/risk-items/bulk-update - Updating {} risk items to status: {}",
                request.riskItemIds().size(), request.status());

        // Validate request
        if (!request.isValid()) {
            log.warn("Invalid bulk update request: missing required fields");
            return ResponseEntity.badRequest().build();
        }

        // Perform bulk update
        DomainRiskAggregationService.BulkUpdateResult result =
                aggregationService.bulkUpdateRiskItemStatus(
                        request.riskItemIds(),
                        request.status(),
                        request.resolution(),
                        request.resolutionComment()
                );

        // Convert service failures to DTO failures
        List<BulkRiskItemUpdateResponse.BulkUpdateFailure> dtoFailures = result.failures().stream()
                .map(f -> new BulkRiskItemUpdateResponse.BulkUpdateFailure(f.riskItemId(), f.reason()))
                .collect(Collectors.toList());

        // Build response
        BulkRiskItemUpdateResponse response = new BulkRiskItemUpdateResponse(
                request.riskItemIds().size(),
                result.successfulIds().size(),
                result.failures().size(),
                result.successfulIds(),
                dtoFailures
        );

        log.info("Bulk update completed: {} succeeded, {} failed out of {} requested",
                response.successCount(), response.failureCount(), response.totalRequested());

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
     * Comprehensive search for risk items with multiple filters.
     * Replaces the old /api/risks/search endpoint functionality.
     *
     * GET /api/v1/risk-items/search
     *
     * Supports filtering by:
     * - appId: Application ID
     * - assignedTo: User assigned to risk item
     * - status: Comma-separated RiskItemStatus values (OPEN, IN_PROGRESS, RESOLVED, WAIVED, CLOSED)
     * - priority: Comma-separated RiskPriority values (CRITICAL, HIGH, MEDIUM, LOW)
     * - fieldKey: Profile field key
     * - severity: Severity string (Critical, High, Medium, Low)
     * - creationType: Comma-separated RiskCreationType values (MANUAL_SME_INITIATED, SYSTEM_AUTO_CREATION)
     * - triggeringEvidenceId: Evidence that triggered this risk
     * - riskRatingDimension: Risk rating dimension (security_rating, confidentiality_rating, availability_rating, etc.)
     * - arb: ARB assignment (security, data, operations, enterprise_architecture)
     * - search: Text search across title, description, hypothesis, condition, consequence
     *
     * Special filtering logic:
     * - When BOTH arb AND assignedTo are provided, uses OR logic:
     *   → Show risks from my ARB OR risks assigned to me
     *   → Example: /search?appId=APM100005&arb=security&assignedTo=security_sme_001
     *   → Returns: All security ARB risks OR any risks assigned to security_sme_001 (even from other ARBs)
     *   → Use case: Guild member wants to see all guild risks + personal assignments from other guilds
     *
     * Supports sorting:
     * - sortBy: Field to sort by (default: priorityScore)
     * - sortOrder: ASC or DESC (default: DESC)
     * - prioritizeUserId: When provided, risks assigned to this user appear first, then sorted by sortBy
     *
     * Supports pagination:
     * - page: Page number (0-indexed, default: 0)
     * - size: Page size (default: 20)
     */
    @GetMapping("/search")
    public ResponseEntity<RiskItemSearchResponse> searchRiskItems(
            @RequestParam(required = false) String appId,
            @RequestParam(required = false) String assignedTo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String fieldKey,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String creationType,
            @RequestParam(required = false) String triggeringEvidenceId,
            @RequestParam(required = false) String riskRatingDimension,
            @RequestParam(required = false) String arb,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "priorityScore") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortOrder,
            @RequestParam(required = false) String prioritizeUserId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("GET /api/v1/risk-items/search - appId: {}, assignedTo: {}, status: {}, priority: {}, " +
                        "fieldKey: {}, severity: {}, creationType: {}, triggeringEvidenceId: {}, " +
                        "riskRatingDimension: {}, arb: {}, search: {}, " +
                        "sortBy: {}, sortOrder: {}, prioritizeUserId: {}, page: {}, size: {}",
                appId, assignedTo, status, priority, fieldKey, severity, creationType,
                triggeringEvidenceId, riskRatingDimension, arb, search, sortBy, sortOrder, prioritizeUserId, page, size);

        // Parse enum filters
        List<RiskItemStatus> statusList = parseStatusList(status);
        List<RiskPriority> priorityList = parsePriorityList(priority);
        List<RiskCreationType> creationTypeList = parseCreationTypeList(creationType);

        // Format search pattern with wildcards (handle in Java to avoid JPQL CONCAT issues)
        String searchPattern = null;
        if (search != null && !search.isBlank()) {
            searchPattern = "%" + search.toLowerCase() + "%";
        }

        // Build sort with prioritization support
        Sort sort;
        if (prioritizeUserId != null && !prioritizeUserId.isBlank()) {
            // Custom sort: assigned to prioritizeUserId first, then by sortBy field
            // JPQL doesn't support CASE in ORDER BY, so we use a workaround with multiple sort orders
            // This will put assigned risks at top, then sort each group by the requested field
            Sort.Direction direction = sortOrder.equalsIgnoreCase("ASC") ? Sort.Direction.ASC : Sort.Direction.DESC;

            // Create a comparator-based sort in memory (will be applied after query)
            sort = Sort.by(direction, sortBy);
        } else {
            sort = Sort.by(
                    sortOrder.equalsIgnoreCase("ASC") ? Sort.Direction.ASC : Sort.Direction.DESC,
                    sortBy
            );
        }

        // Build pageable
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<RiskItem> resultPage;
        List<RiskItemResponse> items;
        long totalElements;
        int totalPages;

        if (prioritizeUserId != null && !prioritizeUserId.isBlank()) {
            // Custom sorting: fetch all results, sort in memory, then paginate
            // This ensures assigned risks appear first across page boundaries
            Pageable unpaged = PageRequest.of(0, Integer.MAX_VALUE, sort);
            Page<RiskItem> allResults = riskItemRepository.searchRiskItems(
                    appId,
                    assignedTo,
                    statusList,
                    priorityList,
                    fieldKey,
                    severity,
                    creationTypeList,
                    triggeringEvidenceId,
                    riskRatingDimension,
                    arb,
                    search,
                    searchPattern,
                    unpaged
            );

            // Sort in memory: assigned to prioritizeUserId first, then by sortBy field
            List<RiskItem> sortedList = allResults.getContent().stream()
                    .sorted((r1, r2) -> {
                        // Primary sort: assigned to prioritizeUserId comes first
                        boolean r1Assigned = prioritizeUserId.equals(r1.getAssignedTo());
                        boolean r2Assigned = prioritizeUserId.equals(r2.getAssignedTo());

                        if (r1Assigned && !r2Assigned) return -1;  // r1 first
                        if (!r1Assigned && r2Assigned) return 1;   // r2 first

                        // Secondary sort: by requested field and direction
                        return compareBySortField(r1, r2, sortBy, sortOrder);
                    })
                    .collect(Collectors.toList());

            // Manual pagination
            totalElements = sortedList.size();
            totalPages = (int) Math.ceil((double) totalElements / size);

            int start = page * size;
            int end = Math.min(start + size, sortedList.size());
            List<RiskItem> pageContent = sortedList.subList(
                    Math.min(start, sortedList.size()),
                    Math.min(end, sortedList.size())
            );

            items = pageContent.stream()
                    .map(RiskDtoMapper::toRiskItemResponse)
                    .collect(Collectors.toList());

        } else {
            // Standard database sorting
            resultPage = riskItemRepository.searchRiskItems(
                    appId,
                    assignedTo,
                    statusList,
                    priorityList,
                    fieldKey,
                    severity,
                    creationTypeList,
                    triggeringEvidenceId,
                    riskRatingDimension,
                    arb,
                    search,
                    searchPattern,
                    pageable
            );

            items = resultPage.getContent().stream()
                    .map(RiskDtoMapper::toRiskItemResponse)
                    .collect(Collectors.toList());

            totalElements = resultPage.getTotalElements();
            totalPages = resultPage.getTotalPages();
        }

        RiskItemSearchResponse response = new RiskItemSearchResponse(
                items,
                page,
                size,
                totalElements,
                totalPages,
                page == 0,
                page >= totalPages - 1
        );

        log.info("Search returned {} risk items (page {}/{})",
                items.size(), page, totalPages);

        return ResponseEntity.ok(response);
    }

    /**
     * Compare two risk items by a specific field for sorting.
     */
    private int compareBySortField(RiskItem r1, RiskItem r2, String sortBy, String sortOrder) {
        int comparison;

        switch (sortBy) {
            case "priorityScore":
                comparison = Integer.compare(
                        r1.getPriorityScore() != null ? r1.getPriorityScore() : 0,
                        r2.getPriorityScore() != null ? r2.getPriorityScore() : 0
                );
                break;
            case "openedAt":
                comparison = r1.getOpenedAt() != null && r2.getOpenedAt() != null
                        ? r1.getOpenedAt().compareTo(r2.getOpenedAt())
                        : 0;
                break;
            case "status":
                comparison = r1.getStatus().compareTo(r2.getStatus());
                break;
            case "priority":
                comparison = r1.getPriority().compareTo(r2.getPriority());
                break;
            default:
                comparison = 0;
        }

        return sortOrder.equalsIgnoreCase("ASC") ? comparison : -comparison;
    }

    /**
     * Parse comma-separated status values into enum list.
     */
    private List<RiskItemStatus> parseStatusList(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return Arrays.stream(status.split(","))
                .map(String::trim)
                .map(String::toUpperCase)
                .map(RiskItemStatus::valueOf)
                .collect(Collectors.toList());
    }

    /**
     * Parse comma-separated priority values into enum list.
     */
    private List<RiskPriority> parsePriorityList(String priority) {
        if (priority == null || priority.isBlank()) {
            return null;
        }
        return Arrays.stream(priority.split(","))
                .map(String::trim)
                .map(String::toUpperCase)
                .map(RiskPriority::valueOf)
                .collect(Collectors.toList());
    }

    /**
     * Parse comma-separated creation type values into enum list.
     */
    private List<RiskCreationType> parseCreationTypeList(String creationType) {
        if (creationType == null || creationType.isBlank()) {
            return null;
        }
        return Arrays.stream(creationType.split(","))
                .map(String::trim)
                .map(String::toUpperCase)
                .map(RiskCreationType::valueOf)
                .collect(Collectors.toList());
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
                request.hypothesis(),
                request.condition(),
                request.consequence(),
                request.controlRefs(),
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

    /**
     * Get complete status history for a risk item.
     * Returns all status transitions in chronological order.
     *
     * GET /api/v1/risk-items/{riskItemId}/status-history
     */
    @GetMapping("/{riskItemId}/status-history")
    public ResponseEntity<List<RiskItemStatusHistory>> getStatusHistory(@PathVariable String riskItemId) {
        log.info("GET /api/v1/risk-items/{}/status-history", riskItemId);

        // Verify risk item exists
        if (!riskItemRepository.existsById(riskItemId)) {
            log.warn("Risk item not found: {}", riskItemId);
            return ResponseEntity.notFound().build();
        }

        // Get status history
        List<RiskItemStatusHistory> history = statusHistoryService.getHistory(riskItemId);

        log.info("Found {} status history records for risk item: {}", history.size(), riskItemId);
        return ResponseEntity.ok(history);
    }

    // ============================================
    // Assignment Endpoints
    // ============================================

    /**
     * Self-assign a risk item to the current user.
     * Automatically transitions status from OPEN to IN_PROGRESS.
     *
     * POST /api/v1/risk-items/{riskItemId}/assign/self
     *
     * Request headers:
     * - X-User-Id: Current user's ID or email (required)
     */
    @PostMapping("/{riskItemId}/assign/self")
    public ResponseEntity<AssignmentResponse> selfAssignRisk(
            @PathVariable String riskItemId,
            @RequestHeader("X-User-Id") String currentUser) {

        log.info("POST /api/v1/risk-items/{}/assign/self - user: {}", riskItemId, currentUser);

        try {
            AssignmentResponse response = assignmentService.selfAssign(riskItemId, currentUser);
            log.info("Successfully self-assigned risk {} to {}", riskItemId, currentUser);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Risk item not found: {}", riskItemId);
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            log.error("Cannot self-assign risk {}: {}", riskItemId, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    /**
     * Assign a risk item to another user.
     * Automatically transitions status from OPEN to IN_PROGRESS.
     *
     * POST /api/v1/risk-items/{riskItemId}/assign
     *
     * Request headers:
     * - X-User-Id: Current user's ID or email (who is doing the assignment)
     *
     * Request body:
     * {
     *   "assignedTo": "user@example.com",
     *   "reason": "You have expertise in this area" (optional)
     * }
     */
    @PostMapping("/{riskItemId}/assign")
    public ResponseEntity<AssignmentResponse> assignRisk(
            @PathVariable String riskItemId,
            @RequestBody AssignRiskItemRequest request,
            @RequestHeader("X-User-Id") String currentUser) {

        log.info("POST /api/v1/risk-items/{}/assign - assignedTo: {}, by: {}",
                riskItemId, request.assignedTo(), currentUser);

        // Validate request
        if (!request.isValid()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            AssignmentResponse response = assignmentService.assignToUser(riskItemId, request, currentUser);
            log.info("Successfully assigned risk {} to {}", riskItemId, request.assignedTo());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Risk item not found: {}", riskItemId);
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            log.error("Cannot assign risk {}: {}", riskItemId, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    /**
     * Unassign a risk item (return to unassigned pool).
     * Automatically transitions status from IN_PROGRESS back to OPEN.
     *
     * DELETE /api/v1/risk-items/{riskItemId}/assign
     *
     * Request headers:
     * - X-User-Id: Current user's ID or email (who is unassigning)
     *
     * Request parameters:
     * - reason: Optional reason for unassignment
     */
    @DeleteMapping("/{riskItemId}/assign")
    public ResponseEntity<AssignmentResponse> unassignRisk(
            @PathVariable String riskItemId,
            @RequestHeader("X-User-Id") String currentUser,
            @RequestParam(required = false) String reason) {

        log.info("DELETE /api/v1/risk-items/{}/assign - by: {}, reason: {}",
                riskItemId, currentUser, reason);

        try {
            AssignmentResponse response = assignmentService.unassign(riskItemId, currentUser, reason);
            log.info("Successfully unassigned risk {}", riskItemId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.error("Risk item not found: {}", riskItemId);
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            log.error("Cannot unassign risk {}: {}", riskItemId, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    /**
     * Get all risk items assigned to the current user.
     * Returns items ordered by priority (high to low).
     *
     * GET /api/v1/risk-items/my-assigned
     *
     * Request headers:
     * - X-User-Id: Current user's ID or email
     */
    @GetMapping("/my-assigned")
    public ResponseEntity<List<RiskItemResponse>> getMyAssignedRisks(
            @RequestHeader("X-User-Id") String currentUser) {

        log.info("GET /api/v1/risk-items/my-assigned - user: {}", currentUser);

        List<RiskItem> riskItems = riskItemRepository.findMyAssignedRisks(currentUser);

        List<RiskItemResponse> responses = riskItems.stream()
                .map(RiskDtoMapper::toRiskItemResponse)
                .collect(Collectors.toList());

        log.info("Found {} assigned risk items for user: {}", responses.size(), currentUser);
        return ResponseEntity.ok(responses);
    }

    /**
     * Get all unassigned risk items (available for assignment).
     * Returns items ordered by priority (high to low).
     *
     * GET /api/v1/risk-items/unassigned
     */
    @GetMapping("/unassigned")
    public ResponseEntity<List<RiskItemResponse>> getUnassignedRisks() {
        log.info("GET /api/v1/risk-items/unassigned");

        List<RiskItem> riskItems = riskItemRepository.findUnassignedRisks();

        List<RiskItemResponse> responses = riskItems.stream()
                .map(RiskDtoMapper::toRiskItemResponse)
                .collect(Collectors.toList());

        log.info("Found {} unassigned risk items", responses.size());
        return ResponseEntity.ok(responses);
    }

    // ============================================
    // SME Review Endpoint
    // ============================================

    /**
     * SME review endpoint for risk items.
     * Combines comment creation, status updates, and reassignment in one operation.
     *
     * PUT /api/v1/risk-items/{riskItemId}/sme-review
     *
     * Supported actions:
     * - approve: Marks risk as SME_APPROVED
     * - approve_with_mitigation: Marks risk as SME_APPROVED with mitigation plan
     * - reject: Marks risk as AWAITING_REMEDIATION (requires remediation)
     * - request_info: Marks risk as AWAITING_REMEDIATION (needs more information)
     * - assign_other: Reassigns risk to another SME
     * - escalate: Marks risk as ESCALATED
     */
    @PutMapping("/{riskItemId}/sme-review")
    public ResponseEntity<SmeReviewResponse> smeReview(
            @PathVariable String riskItemId,
            @RequestBody SmeReviewRequest request) {

        log.info("PUT /api/v1/risk-items/{}/sme-review - action: {}, smeId: {}",
                riskItemId, request.action(), request.smeId());

        // Validate request
        if (!request.isValid()) {
            String validationError = getValidationError(request);
            log.warn("Invalid SME review request for riskItemId {}: {} - Request: {}",
                    riskItemId, validationError, request);
            return ResponseEntity.badRequest()
                    .body(new SmeReviewResponse(
                            riskItemId,
                            "VALIDATION_ERROR",
                            request.smeId(),
                            OffsetDateTime.now(),
                            validationError
                    ));
        }

        // Verify risk item exists
        if (!riskItemRepository.existsById(riskItemId)) {
            log.warn("Risk item not found: {}", riskItemId);
            return ResponseEntity.notFound().build();
        }

        // Add SME comment
        String commentText = buildCommentText(request);
        RiskComment comment = new RiskComment();
        comment.setCommentId("comment_" + UUID.randomUUID());
        comment.setRiskItemId(riskItemId);
        comment.setCommentType(RiskCommentType.REVIEW);
        comment.setCommentText(commentText);
        comment.setCommentedBy(request.smeId());
        comment.setIsInternal(false);
        comment.setCommentedAt(OffsetDateTime.now());
        riskCommentRepository.save(comment);

        // Handle different actions
        String status;
        if (request.isApprove() || request.isApproveWithMitigation()) {
            // Approve: SME accepts the risk
            String resolutionComment = request.isApproveWithMitigation()
                    ? "SME approved with mitigation plan: " + request.mitigationPlan()
                    : "SME approved by " + request.smeId();
            if (request.comments() != null) {
                resolutionComment += " - " + request.comments();
            }

            aggregationService.updateRiskItemStatus(
                    riskItemId,
                    RiskItemStatus.SME_APPROVED,
                    "SME_APPROVED",
                    resolutionComment,
                    request.smeId(),
                    "SME"
            );
            status = "SME_APPROVED";
            log.info("SME approved risk item: {} (mitigation: {})", riskItemId, request.isApproveWithMitigation());

        } else if (request.isReject()) {
            // Reject: SME requires remediation
            aggregationService.updateRiskItemStatus(
                    riskItemId,
                    RiskItemStatus.AWAITING_REMEDIATION,
                    "SME_REJECTED",
                    "SME rejected by " + request.smeId() + ": " +
                            (request.comments() != null ? request.comments() : "Requires remediation"),
                    request.smeId(),
                    "SME"
            );
            status = "SME_REJECTED";
            log.info("SME rejected risk item: {}", riskItemId);

        } else if (request.isRequestInfo()) {
            // Request info: Send back to PO to provide additional information
            aggregationService.updateRiskItemStatus(
                    riskItemId,
                    RiskItemStatus.AWAITING_REMEDIATION,
                    "INFO_REQUESTED",
                    "SME " + request.smeId() + " requested more information: " +
                            (request.comments() != null ? request.comments() : "Additional information needed"),
                    request.smeId(),
                    "SME"
            );
            status = "INFO_REQUESTED";
            log.info("SME requested info for risk item: {}", riskItemId);

        } else if (request.isAssignOther()) {
            // Reassign to another SME - use assignment service
            try {
                AssignRiskItemRequest assignRequest = new AssignRiskItemRequest(
                        request.assignToSme(),
                        "Reassigned by " + request.smeId() + ": " +
                                (request.comments() != null ? request.comments() : "Reassignment")
                );
                assignmentService.assignToUser(riskItemId, assignRequest, request.smeId());
                status = "REASSIGNED";
                log.info("SME reassigned risk item: {} to {}", riskItemId, request.assignToSme());
            } catch (Exception e) {
                log.error("Failed to reassign risk item: {}", riskItemId, e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

        } else if (request.isEscalate()) {
            // Escalate: Mark as ESCALATED (active state, awaiting resolution)
            aggregationService.updateRiskItemStatus(
                    riskItemId,
                    RiskItemStatus.ESCALATED,
                    "ESCALATED",
                    "Risk escalated by " + request.smeId() + ": " +
                            (request.comments() != null ? request.comments() : "Escalated for review"),
                    request.smeId(),
                    "SME"
            );
            status = "ESCALATED";
            log.info("SME escalated risk item: {}", riskItemId);

        } else {
            log.warn("Unhandled action: {}", request.action());
            return ResponseEntity.badRequest().build();
        }

        // Build response
        OffsetDateTime reviewedAt = OffsetDateTime.now();
        SmeReviewResponse response = new SmeReviewResponse(
                riskItemId,
                status,
                request.smeId(),
                reviewedAt
        );

        log.info("SME review completed for risk item: {} - status: {}", riskItemId, status);
        return ResponseEntity.ok(response);
    }

    /**
     * Build comment text based on action and request data.
     */
    private String buildCommentText(SmeReviewRequest request) {
        StringBuilder sb = new StringBuilder();

        if (request.isApprove()) {
            sb.append("✅ SME Approved");
        } else if (request.isApproveWithMitigation()) {
            sb.append("✅ SME Approved with Mitigation\n\n");
            sb.append("**Mitigation Plan:**\n").append(request.mitigationPlan());
        } else if (request.isReject()) {
            sb.append("❌ SME Rejected");
        } else if (request.isRequestInfo()) {
            sb.append("ℹ️ SME Requested More Information");
        } else if (request.isAssignOther()) {
            sb.append("🔄 Reassigned to ").append(request.assignToSme());
        } else if (request.isEscalate()) {
            sb.append("⬆️ SME Escalated");
        }

        if (request.comments() != null && !request.comments().isBlank()) {
            sb.append("\n\n**Comments:**\n").append(request.comments());
        }

        return sb.toString();
    }

    /**
     * Get detailed validation error message for logging and response.
     */
    private String getValidationError(SmeReviewRequest request) {
        if (request.action() == null || request.action().isBlank()) {
            return "Missing required field: 'action' (must be one of: approve, approve_with_mitigation, reject, request_info, assign_other, escalate)";
        }

        if (request.smeId() == null || request.smeId().isBlank()) {
            return "Missing required field: 'smeId'";
        }

        String lowerAction = request.action().toLowerCase();
        boolean validAction = lowerAction.equals("approve")
                || lowerAction.equals("approve_with_mitigation")
                || lowerAction.equals("reject")
                || lowerAction.equals("request_info")
                || lowerAction.equals("assign_other")
                || lowerAction.equals("escalate");

        if (!validAction) {
            return "Invalid action: '" + request.action() + "' (must be one of: approve, approve_with_mitigation, reject, request_info, assign_other, escalate)";
        }

        if (lowerAction.equals("approve_with_mitigation") && (request.mitigationPlan() == null || request.mitigationPlan().isBlank())) {
            return "Missing required field: 'mitigationPlan' (required for action 'approve_with_mitigation')";
        }

        if (lowerAction.equals("assign_other") && (request.assignToSme() == null || request.assignToSme().isBlank())) {
            return "Missing required field: 'assignToSme' (required for action 'assign_other')";
        }

        return "Invalid request";
    }
}
