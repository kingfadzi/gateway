package com.example.gateway.risk.service;

import com.example.gateway.risk.model.*;
import com.example.gateway.risk.repository.DomainRiskRepository;
import com.example.gateway.risk.repository.RiskItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing domain-level risk aggregations.
 *
 * Responsibilities:
 * - Create or retrieve domain risks (one per app+domain combination)
 * - Add risk items to domain risks
 * - Recalculate aggregated fields (counts, scores, priorities)
 * - Manage domain risk lifecycle and status transitions
 */
@Service
public class DomainRiskAggregationService {

    private static final Logger log = LoggerFactory.getLogger(DomainRiskAggregationService.class);

    private final DomainRiskRepository domainRiskRepository;
    private final RiskItemRepository riskItemRepository;
    private final RiskPriorityCalculator priorityCalculator;
    private final ArbRoutingService arbRoutingService;

    public DomainRiskAggregationService(
            DomainRiskRepository domainRiskRepository,
            RiskItemRepository riskItemRepository,
            RiskPriorityCalculator priorityCalculator,
            ArbRoutingService arbRoutingService) {
        this.domainRiskRepository = domainRiskRepository;
        this.riskItemRepository = riskItemRepository;
        this.priorityCalculator = priorityCalculator;
        this.arbRoutingService = arbRoutingService;
    }

    /**
     * Get or create a domain risk for the given app and derived_from field.
     * One domain risk exists per app+domain combination.
     *
     * @param appId Application ID
     * @param derivedFrom The derived_from field (e.g., "security_rating")
     * @return DomainRisk entity
     */
    @Transactional
    public DomainRisk getOrCreateDomainRisk(String appId, String derivedFrom) {
        // Use full derived_from as risk_rating_dimension (no transformation)
        String riskRatingDimension = derivedFrom;
        String arb = arbRoutingService.getArbForDerivedFrom(derivedFrom);

        return domainRiskRepository.findByAppIdAndRiskRatingDimension(appId, riskRatingDimension)
                .orElseGet(() -> createNewDomainRisk(appId, riskRatingDimension, derivedFrom, arb));
    }

    /**
     * Create a new domain risk.
     */
    private DomainRisk createNewDomainRisk(String appId, String riskRatingDimension, String derivedFrom, String arb) {
        DomainRisk domainRisk = new DomainRisk();
        domainRisk.setDomainRiskId(UUID.randomUUID().toString());
        domainRisk.setAppId(appId);
        domainRisk.setRiskRatingDimension(riskRatingDimension);
        domainRisk.setDerivedFrom(derivedFrom);
        domainRisk.setArb(arb);
        domainRisk.setAssignedArb(arb);  // Auto-assign to ARB
        domainRisk.setStatus(DomainRiskStatus.PENDING_ARB_REVIEW);
        domainRisk.setOpenedAt(OffsetDateTime.now());
        domainRisk.setAssignedAt(OffsetDateTime.now());

        // Initialize counters
        domainRisk.setTotalItems(0);
        domainRisk.setOpenItems(0);
        domainRisk.setHighPriorityItems(0);
        domainRisk.setPriorityScore(0);

        // Generate title and description
        domainRisk.setTitle(generateDomainRiskTitle(riskRatingDimension));
        domainRisk.setDescription(generateDomainRiskDescription(riskRatingDimension, derivedFrom));

        DomainRisk saved = domainRiskRepository.save(domainRisk);
        log.info("Created new domain risk: {} for app: {}, risk_rating_dimension: {}", saved.getDomainRiskId(), appId, riskRatingDimension);

        return saved;
    }

    /**
     * Add a risk item to a domain risk and recalculate aggregations.
     *
     * @param domainRisk The domain risk to add to
     * @param riskItem The risk item to add
     */
    @Transactional
    public void addRiskItemToDomain(DomainRisk domainRisk, RiskItem riskItem) {
        // Link risk item to domain risk
        riskItem.setDomainRiskId(domainRisk.getDomainRiskId());

        // Denormalize risk_rating_dimension and arb from domain risk
        riskItem.setRiskRatingDimension(domainRisk.getRiskRatingDimension());
        riskItem.setArb(domainRisk.getArb());

        // Save risk item
        riskItemRepository.save(riskItem);

        // Update domain risk timestamp
        domainRisk.setLastItemAddedAt(OffsetDateTime.now());

        // Recalculate aggregations
        recalculateAggregations(domainRisk);

        log.info("Added risk item {} to domain risk {}", riskItem.getRiskItemId(), domainRisk.getDomainRiskId());
    }

    /**
     * Recalculate all aggregated fields for a domain risk.
     * Called after risk items are added, removed, or updated.
     *
     * @param domainRisk The domain risk to recalculate
     */
    @Transactional
    public void recalculateAggregations(DomainRisk domainRisk) {
        String domainRiskId = domainRisk.getDomainRiskId();

        // Get all risk items for this domain risk
        List<RiskItem> allItems = riskItemRepository.findByDomainRiskId(domainRiskId);

        // Calculate counts
        int totalItems = allItems.size();
        long openItems = riskItemRepository.countByDomainRiskIdAndStatus(
                domainRiskId, RiskItemStatus.OPEN) +
                riskItemRepository.countByDomainRiskIdAndStatus(
                        domainRiskId, RiskItemStatus.IN_PROGRESS);

        long highPriorityItems = riskItemRepository.countHighPriorityItems(domainRiskId);

        // Get maximum priority score from open items
        Integer maxScore = riskItemRepository.getMaxPriorityScore(domainRiskId);
        if (maxScore == null) {
            maxScore = 0;
        }

        // Calculate domain-level priority score
        int domainScore = priorityCalculator.calculateDomainPriorityScore(
                maxScore,
                (int) highPriorityItems,
                (int) openItems
        );

        // Determine overall priority and severity
        RiskPriority overallPriority = priorityCalculator.getPriorityFromScore(domainScore);
        String overallSeverity = priorityCalculator.getSeverityLabel(domainScore);

        // Update domain risk
        domainRisk.setTotalItems(totalItems);
        domainRisk.setOpenItems((int) openItems);
        domainRisk.setHighPriorityItems((int) highPriorityItems);
        domainRisk.setPriorityScore(domainScore);
        domainRisk.setOverallPriority(overallPriority.name());
        domainRisk.setOverallSeverity(overallSeverity);

        // Auto-transition status if all items are resolved
        if (openItems == 0 && totalItems > 0) {
            if (domainRisk.getStatus() != DomainRiskStatus.RESOLVED &&
                    domainRisk.getStatus() != DomainRiskStatus.CLOSED) {
                domainRisk.setStatus(DomainRiskStatus.RESOLVED);
                log.info("Domain risk {} auto-resolved: all items closed", domainRiskId);
            }
        } else if (openItems > 0) {
            // If there are open items and status is resolved, move back to in_progress
            if (domainRisk.getStatus() == DomainRiskStatus.RESOLVED) {
                domainRisk.setStatus(DomainRiskStatus.IN_PROGRESS);
                log.info("Domain risk {} reopened: new items added", domainRiskId);
            }
        }

        domainRiskRepository.save(domainRisk);

        log.debug("Recalculated aggregations for domain risk {}: total={}, open={}, highPriority={}, score={}",
                domainRiskId, totalItems, openItems, highPriorityItems, domainScore);
    }

    /**
     * Update status of a risk item and recalculate domain aggregations.
     *
     * @param riskItemId Risk item ID
     * @param newStatus New status
     * @param resolution Resolution type (if being resolved/closed)
     * @param resolutionComment Comment explaining resolution
     */
    @Transactional
    public void updateRiskItemStatus(String riskItemId, RiskItemStatus newStatus,
                                      String resolution, String resolutionComment) {
        RiskItem riskItem = riskItemRepository.findById(riskItemId)
                .orElseThrow(() -> new IllegalArgumentException("Risk item not found: " + riskItemId));

        RiskItemStatus oldStatus = riskItem.getStatus();
        riskItem.setStatus(newStatus);

        if (newStatus == RiskItemStatus.RESOLVED || newStatus == RiskItemStatus.CLOSED) {
            riskItem.setResolvedAt(OffsetDateTime.now());
            riskItem.setResolution(resolution);
            riskItem.setResolutionComment(resolutionComment);
        }

        riskItemRepository.save(riskItem);

        // Recalculate domain risk if status changed
        if (oldStatus != newStatus) {
            String domainRiskId = riskItem.getDomainRiskId();
            DomainRisk domainRisk = domainRiskRepository.findById(domainRiskId)
                    .orElseThrow(() -> new IllegalStateException("Domain risk not found: " + domainRiskId));

            recalculateAggregations(domainRisk);

            log.info("Updated risk item {} status from {} to {}", riskItemId, oldStatus, newStatus);
        }
    }

    /**
     * Get all domain risks for an ARB, prioritized by score.
     *
     * @param arbName ARB identifier (e.g., "security_arb")
     * @param statuses List of statuses to include
     * @return List of domain risks
     */
    public List<DomainRisk> getDomainRisksForArb(String arbName, List<DomainRiskStatus> statuses) {
        return domainRiskRepository.findByArbPrioritized(arbName, statuses);
    }

    /**
     * Get all risk items for a domain risk, prioritized by score.
     *
     * @param domainRiskId Domain risk ID
     * @return List of risk items
     */
    public List<RiskItem> getRiskItemsForDomain(String domainRiskId) {
        return riskItemRepository.findByDomainRiskId(domainRiskId);
    }

    /**
     * Get all risk items for an app, prioritized by score.
     *
     * @param appId Application ID
     * @return List of risk items
     */
    public List<RiskItem> getRiskItemsForApp(String appId) {
        return riskItemRepository.findByAppIdOrderByPriorityScoreDesc(appId);
    }

    /**
     * Create a risk item manually (not triggered by evidence submission).
     * Used by ARB/SME to create risks outside the automatic flow.
     *
     * @param appId Application ID
     * @param fieldKey Field key
     * @param profileFieldId Profile field ID (optional)
     * @param title Risk title
     * @param description Risk description
     * @param hypothesis Rich content: hypothesis (optional)
     * @param condition Rich content: condition (optional)
     * @param consequence Rich content: consequence (optional)
     * @param controlRefs Rich content: control references (optional)
     * @param priority Priority level
     * @param createdBy User creating the risk
     * @param evidenceId Evidence ID (optional)
     * @param derivedFrom Derived from field for domain routing
     * @return Created risk item
     */
    @Transactional
    public RiskItem createManualRisk(String appId, String fieldKey, String profileFieldId,
                                      String title, String description,
                                      String hypothesis, String condition, String consequence, String controlRefs,
                                      RiskPriority priority,
                                      String createdBy, String evidenceId, String derivedFrom) {

        // Get or create domain risk
        DomainRisk domainRisk = getOrCreateDomainRisk(appId, derivedFrom);

        // Calculate priority score (no evidence status multiplier for manual creation)
        int priorityScore = priorityCalculator.calculatePriorityScore(priority, "approved");
        String severity = priorityCalculator.getSeverityLabel(priorityScore);

        // Create risk item
        RiskItem riskItem = new RiskItem();
        riskItem.setRiskItemId("item_" + UUID.randomUUID());
        riskItem.setAppId(appId);
        riskItem.setFieldKey(fieldKey);
        riskItem.setProfileFieldId(profileFieldId);
        riskItem.setTriggeringEvidenceId(evidenceId);  // May be null

        riskItem.setTitle(title);
        riskItem.setDescription(description);

        // Set rich content fields
        riskItem.setHypothesis(hypothesis);
        riskItem.setCondition(condition);
        riskItem.setConsequence(consequence);
        riskItem.setControlRefs(controlRefs);

        riskItem.setPriority(priority);
        riskItem.setSeverity(severity);
        riskItem.setPriorityScore(priorityScore);
        riskItem.setEvidenceStatus("approved");  // Default for manual creation

        riskItem.setStatus(RiskItemStatus.OPEN);
        riskItem.setCreationType(RiskCreationType.MANUAL_CREATION);
        riskItem.setRaisedBy(createdBy);
        riskItem.setOpenedAt(OffsetDateTime.now());

        // Add to domain risk
        addRiskItemToDomain(domainRisk, riskItem);

        log.info("Manually created risk item: {} by {} for app: {}",
                 riskItem.getRiskItemId(), createdBy, appId);

        return riskItem;
    }

    /**
     * Reassign a domain risk to a different ARB.
     *
     * @param domainRiskId Domain risk ID
     * @param newArb New ARB identifier
     * @param assignedBy User performing the reassignment
     * @return Updated domain risk
     */
    @Transactional
    public DomainRisk reassignDomainRisk(String domainRiskId, String newArb, String assignedBy) {
        DomainRisk domainRisk = domainRiskRepository.findById(domainRiskId)
                .orElseThrow(() -> new IllegalArgumentException("Domain risk not found: " + domainRiskId));

        String oldArb = domainRisk.getAssignedArb();
        domainRisk.setAssignedArb(newArb);
        domainRisk.setAssignedAt(OffsetDateTime.now());

        DomainRisk saved = domainRiskRepository.save(domainRisk);

        log.info("Reassigned domain risk {} from {} to {} by {}",
                 domainRiskId, oldArb, newArb, assignedBy);

        return saved;
    }

    // =====================
    // Helper Methods
    // =====================

    private String generateDomainRiskTitle(String domain) {
        return domain.substring(0, 1).toUpperCase() + domain.substring(1) + " Domain Risks";
    }

    private String generateDomainRiskDescription(String domain, String derivedFrom) {
        return String.format("Aggregated %s risks derived from %s assessment. " +
                        "Review and remediate individual risk items to improve overall compliance posture.",
                domain, derivedFrom);
    }

    /**
     * Bulk update multiple risk items with the same status change.
     * Applies the update to each risk item and recalculates domain aggregations.
     *
     * @param riskItemIds List of risk item IDs to update
     * @param newStatus New status to apply
     * @param resolution Resolution type (if being resolved/closed)
     * @param resolutionComment Comment explaining resolution
     * @return Result containing successful IDs and failures
     */
    @Transactional
    public BulkUpdateResult bulkUpdateRiskItemStatus(List<String> riskItemIds, RiskItemStatus newStatus,
                                         String resolution, String resolutionComment) {
        java.util.List<String> successfulIds = new java.util.ArrayList<>();
        java.util.List<BulkUpdateFailure> failures = new java.util.ArrayList<>();

        if (riskItemIds == null || riskItemIds.isEmpty()) {
            return new BulkUpdateResult(successfulIds, failures);
        }

        for (String riskItemId : riskItemIds) {
            try {
                updateRiskItemStatus(riskItemId, newStatus, resolution, resolutionComment);
                successfulIds.add(riskItemId);
            } catch (IllegalArgumentException e) {
                log.error("Risk item not found in bulk update: {}", riskItemId);
                failures.add(new BulkUpdateFailure(riskItemId, "Risk item not found"));
            } catch (Exception e) {
                log.error("Failed to update risk item {} in bulk update: {}", riskItemId, e.getMessage());
                failures.add(new BulkUpdateFailure(riskItemId, e.getMessage()));
            }
        }

        log.info("Bulk updated {} of {} risk items to status: {}", successfulIds.size(), riskItemIds.size(), newStatus);
        return new BulkUpdateResult(successfulIds, failures);
    }

    /**
     * Result holder for bulk update operations.
     */
    public record BulkUpdateResult(
            java.util.List<String> successfulIds,
            java.util.List<BulkUpdateFailure> failures
    ) {}

    /**
     * Individual failure details for bulk operations.
     */
    public record BulkUpdateFailure(
            String riskItemId,
            String reason
    ) {}
}
