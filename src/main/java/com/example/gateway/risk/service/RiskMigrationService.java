package com.example.gateway.risk.service;

import com.example.gateway.profile.service.ProfileFieldRegistryService;
import com.example.gateway.risk.model.*;
import com.example.gateway.risk.repository.DomainRiskRepository;
import com.example.gateway.risk.repository.RiskItemRepository;
import com.example.gateway.risk.repository.RiskStoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for migrating legacy risk_story records to the new v2 model.
 *
 * Migration strategy:
 * 1. Load all risk_story records
 * 2. Group by app + domain (calculated from field_key)
 * 3. Create domain_risk for each unique app+domain
 * 4. Convert each risk_story to risk_item
 * 5. Link items to domains and calculate aggregations
 */
@Service
public class RiskMigrationService {

    private static final Logger log = LoggerFactory.getLogger(RiskMigrationService.class);

    private final RiskStoryRepository riskStoryRepository;
    private final DomainRiskRepository domainRiskRepository;
    private final RiskItemRepository riskItemRepository;
    private final ProfileFieldRegistryService registryService;
    private final ArbRoutingService arbRoutingService;
    private final RiskPriorityCalculator priorityCalculator;
    private final DomainRiskAggregationService aggregationService;
    private final StatusHistoryService statusHistoryService;

    public RiskMigrationService(
            RiskStoryRepository riskStoryRepository,
            DomainRiskRepository domainRiskRepository,
            RiskItemRepository riskItemRepository,
            ProfileFieldRegistryService registryService,
            ArbRoutingService arbRoutingService,
            RiskPriorityCalculator priorityCalculator,
            DomainRiskAggregationService aggregationService,
            StatusHistoryService statusHistoryService) {
        this.riskStoryRepository = riskStoryRepository;
        this.domainRiskRepository = domainRiskRepository;
        this.riskItemRepository = riskItemRepository;
        this.registryService = registryService;
        this.arbRoutingService = arbRoutingService;
        this.priorityCalculator = priorityCalculator;
        this.aggregationService = aggregationService;
        this.statusHistoryService = statusHistoryService;
    }

    /**
     * Migrate all risk_story records to the v2 model.
     *
     * @param dryRun If true, only log what would be done without actually migrating
     * @return Migration result with statistics
     */
    @Transactional
    public MigrationResult migrateAllRisks(boolean dryRun) {
        log.info("Starting risk migration (dryRun={})", dryRun);

        MigrationResult result = new MigrationResult();
        result.setStartTime(OffsetDateTime.now());

        try {
            // Load all risk_story records
            List<RiskStory> riskStories = riskStoryRepository.findAll();
            result.setTotalRiskStories(riskStories.size());

            log.info("Found {} risk_story records to migrate", riskStories.size());

            if (riskStories.isEmpty()) {
                log.info("No risk stories to migrate");
                result.setEndTime(OffsetDateTime.now());
                return result;
            }

            // Group by app + domain
            Map<String, List<RiskStory>> groupedByAppAndDomain = groupByAppAndDomain(riskStories);
            result.setDomainRisksToCreate(groupedByAppAndDomain.size());

            log.info("Grouped into {} unique app+domain combinations", groupedByAppAndDomain.size());

            if (!dryRun) {
                // Migrate each group
                for (Map.Entry<String, List<RiskStory>> entry : groupedByAppAndDomain.entrySet()) {
                    String key = entry.getKey();
                    List<RiskStory> stories = entry.getValue();

                    try {
                        migrateDomainGroup(key, stories, result);
                    } catch (Exception e) {
                        log.error("Failed to migrate group {}: {}", key, e.getMessage(), e);
                        result.getFailedGroups().add(key);
                        result.setFailedRiskItems(result.getFailedRiskItems() + stories.size());
                    }
                }
            } else {
                // Dry run - just log what would be done
                for (Map.Entry<String, List<RiskStory>> entry : groupedByAppAndDomain.entrySet()) {
                    String key = entry.getKey();
                    List<RiskStory> stories = entry.getValue();
                    log.info("Would migrate group {}: {} risk stories", key, stories.size());
                }
            }

            result.setEndTime(OffsetDateTime.now());
            result.setSuccess(result.getFailedGroups().isEmpty());

            log.info("Migration completed: {}", result);
            return result;

        } catch (Exception e) {
            log.error("Migration failed with error: {}", e.getMessage(), e);
            result.setEndTime(OffsetDateTime.now());
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            return result;
        }
    }

    /**
     * Group risk stories by app + domain.
     * Domain is calculated from field_key using registry lookup.
     */
    private Map<String, List<RiskStory>> groupByAppAndDomain(List<RiskStory> riskStories) {
        return riskStories.stream()
                .collect(Collectors.groupingBy(story -> {
                    String appId = story.getAppId();
                    String domain = getDomainForStory(story);
                    return appId + "|" + domain;
                }));
    }

    /**
     * Migrate a group of risk stories for one app+domain combination.
     */
    private void migrateDomainGroup(String groupKey, List<RiskStory> stories, MigrationResult result) {
        if (stories.isEmpty()) {
            return;
        }

        // Extract app and domain from group key
        String[] parts = groupKey.split("\\|");
        String appId = parts[0];
        String domain = parts[1];

        log.info("Migrating group: app={}, domain={}, stories={}", appId, domain, stories.size());

        // Get derived_from for the first story (all should be same domain)
        RiskStory firstStory = stories.get(0);
        String derivedFrom = getDerivedFromForStory(firstStory);

        // Get or create domain risk
        DomainRisk domainRisk = aggregationService.getOrCreateDomainRisk(appId, derivedFrom);
        result.setCreatedDomainRisks(result.getCreatedDomainRisks() + 1);

        log.debug("Using domain risk: {}", domainRisk.getDomainRiskId());

        // Convert each story to risk item
        int migratedCount = 0;
        for (RiskStory story : stories) {
            try {
                RiskItem riskItem = convertToRiskItem(story, domainRisk);

                // Check for duplicates
                boolean exists = riskItemRepository.existsByAppIdAndFieldKeyAndTriggeringEvidenceId(
                        riskItem.getAppId(),
                        riskItem.getFieldKey(),
                        riskItem.getTriggeringEvidenceId());

                if (!exists) {
                    RiskItem saved = riskItemRepository.save(riskItem);

                    // Log initial status to history
                    String triggerReason = String.format("Migrated from risk_story %s (original status: %s)",
                            story.getRiskId(), story.getStatus());
                    statusHistoryService.logSystemCreation(saved.getRiskItemId(), triggerReason);

                    migratedCount++;
                    result.setCreatedRiskItems(result.getCreatedRiskItems() + 1);
                } else {
                    log.debug("Skipping duplicate risk item for evidence: {}", riskItem.getTriggeringEvidenceId());
                    result.setSkippedRiskItems(result.getSkippedRiskItems() + 1);
                }

            } catch (Exception e) {
                log.error("Failed to migrate risk story {}: {}", story.getRiskId(), e.getMessage());
                result.setFailedRiskItems(result.getFailedRiskItems() + 1);
            }
        }

        log.info("Migrated {}/{} risk items for domain risk {}",
                 migratedCount, stories.size(), domainRisk.getDomainRiskId());

        // Recalculate aggregations for this domain risk
        aggregationService.recalculateAggregations(domainRisk);
    }

    /**
     * Convert a RiskStory to a RiskItem.
     */
    private RiskItem convertToRiskItem(RiskStory story, DomainRisk domainRisk) {
        RiskItem item = new RiskItem();

        // IDs and references
        item.setRiskItemId("migrated_" + story.getRiskId());
        item.setDomainRiskId(domainRisk.getDomainRiskId());
        item.setAppId(story.getAppId());
        item.setFieldKey(story.getFieldKey());
        item.setProfileFieldId(story.getProfileFieldId());
        item.setTriggeringEvidenceId(story.getTriggeringEvidenceId());
        item.setTrackId(story.getTrackId());

        // Content
        item.setTitle(story.getTitle());
        item.setDescription(buildDescription(story));

        // Priority & severity - calculate from registry if possible
        RiskPriority priority = determinePriority(story);
        String evidenceStatus = determineEvidenceStatus(story);
        int priorityScore = priorityCalculator.calculatePriorityScore(priority, evidenceStatus);
        String severity = story.getSeverity() != null ? story.getSeverity() : "medium";

        item.setPriority(priority);
        item.setSeverity(severity);
        item.setPriorityScore(priorityScore);
        item.setEvidenceStatus(evidenceStatus);

        // Status - map old status to new
        item.setStatus(mapStatus(story.getStatus()));
        item.setResolution(story.getClosureReason());

        // Lifecycle
        item.setCreationType(story.getCreationType() != null ? story.getCreationType() : RiskCreationType.SYSTEM_AUTO_CREATION);
        item.setRaisedBy(story.getRaisedBy());
        item.setOpenedAt(story.getOpenedAt() != null ? story.getOpenedAt() : OffsetDateTime.now());
        item.setResolvedAt(story.getClosedAt());

        // Snapshot
        item.setPolicyRequirementSnapshot(story.getPolicyRequirementSnapshot());

        return item;
    }

    /**
     * Determine domain for a risk story based on field_key.
     */
    private String getDomainForStory(RiskStory story) {
        String derivedFrom = getDerivedFromForStory(story);
        return arbRoutingService.calculateDomain(derivedFrom);
    }

    /**
     * Get derived_from for a risk story by looking up field in registry.
     */
    private String getDerivedFromForStory(RiskStory story) {
        String fieldKey = story.getFieldKey();

        // Try to get from registry
        Optional<String> derivedFrom = registryService.getDerivedFromForField(fieldKey);

        if (derivedFrom.isPresent()) {
            return derivedFrom.get();
        }

        // Fallback: infer from field key patterns
        if (fieldKey.contains("security") || fieldKey.contains("encryption") || fieldKey.contains("mfa")) {
            return "security_rating";
        } else if (fieldKey.contains("integrity") || fieldKey.contains("audit") || fieldKey.contains("validation")) {
            return "integrity_rating";
        } else if (fieldKey.contains("availability") || fieldKey.contains("rto") || fieldKey.contains("rpo")) {
            return "availability_rating";
        } else if (fieldKey.contains("resilience") || fieldKey.contains("backup") || fieldKey.contains("dr")) {
            return "resilience_rating";
        } else if (fieldKey.contains("confidentiality") || fieldKey.contains("data")) {
            return "confidentiality_rating";
        } else {
            log.warn("Could not determine derived_from for field_key: {}, defaulting to app_criticality_assessment", fieldKey);
            return "app_criticality_assessment";
        }
    }

    /**
     * Determine priority from risk story.
     */
    private RiskPriority determinePriority(RiskStory story) {
        // Try to infer from severity
        String severity = story.getSeverity();
        if (severity != null) {
            return switch (severity.toLowerCase()) {
                case "critical" -> RiskPriority.CRITICAL;
                case "high" -> RiskPriority.HIGH;
                case "medium" -> RiskPriority.MEDIUM;
                case "low" -> RiskPriority.LOW;
                default -> RiskPriority.MEDIUM;
            };
        }

        // Default to MEDIUM
        return RiskPriority.MEDIUM;
    }

    /**
     * Determine evidence status from risk story context.
     */
    private String determineEvidenceStatus(RiskStory story) {
        // If risk was auto-created, evidence is likely missing or non-compliant
        if (story.getCreationType() == RiskCreationType.SYSTEM_AUTO_CREATION) {
            return "missing";
        }

        // If approved, evidence was provided and accepted
        if (story.getStatus() == RiskStatus.APPROVED) {
            return "approved";
        }

        // If closed, evidence was likely provided
        if (story.getStatus() == RiskStatus.CLOSED) {
            return "approved";
        }

        // Default to missing
        return "missing";
    }

    /**
     * Map old RiskStatus to new RiskItemStatus.
     */
    private RiskItemStatus mapStatus(RiskStatus oldStatus) {
        if (oldStatus == null) {
            return RiskItemStatus.PENDING_REVIEW;
        }

        return switch (oldStatus) {
            case PENDING_SME_REVIEW, UNDER_REVIEW -> RiskItemStatus.PENDING_REVIEW;
            case AWAITING_EVIDENCE -> RiskItemStatus.IN_REMEDIATION;
            case APPROVED -> RiskItemStatus.REMEDIATED;
            case WAIVED -> RiskItemStatus.SME_APPROVED;
            case REJECTED, CLOSED -> RiskItemStatus.CLOSED;
            default -> RiskItemStatus.PENDING_REVIEW;
        };
    }

    /**
     * Build description from risk story fields.
     */
    private String buildDescription(RiskStory story) {
        StringBuilder desc = new StringBuilder();

        if (story.getHypothesis() != null && !story.getHypothesis().isEmpty()) {
            desc.append(story.getHypothesis());
        }

        if (story.getCondition() != null && !story.getCondition().isEmpty()) {
            if (desc.length() > 0) desc.append(" ");
            desc.append("IF: ").append(story.getCondition());
        }

        if (story.getConsequence() != null && !story.getConsequence().isEmpty()) {
            if (desc.length() > 0) desc.append(" ");
            desc.append("THEN: ").append(story.getConsequence());
        }

        return desc.length() > 0 ? desc.toString() : "Migrated from risk_story";
    }

    /**
     * Backfill status history for already-migrated risk items that are missing history records.
     * This creates an initial status history entry for each risk item that doesn't have one.
     *
     * @return Number of status history records created
     */
    @Transactional
    public int backfillStatusHistory() {
        log.info("Starting status history backfill for migrated risk items");

        // Find all migrated risk items (those with IDs starting with "migrated_")
        List<RiskItem> migratedItems = riskItemRepository.findAll().stream()
                .filter(item -> item.getRiskItemId().startsWith("migrated_"))
                .toList();

        log.info("Found {} migrated risk items", migratedItems.size());

        int backfilledCount = 0;
        for (RiskItem item : migratedItems) {
            try {
                // Check if this item already has status history
                long historyCount = statusHistoryService.countTransitions(item.getRiskItemId());

                if (historyCount == 0) {
                    // No history exists, create initial record
                    String triggerReason = String.format(
                            "Backfilled from migration (current status: %s, opened: %s)",
                            item.getStatus(),
                            item.getOpenedAt()
                    );

                    statusHistoryService.logStatusChange(
                            item.getRiskItemId(),
                            null,  // No previous status
                            item.getStatus(),
                            "MIGRATION_BACKFILL",
                            triggerReason,
                            "SYSTEM",
                            "SYSTEM"
                    );

                    backfilledCount++;
                    log.debug("Backfilled status history for risk item: {}", item.getRiskItemId());
                }
            } catch (Exception e) {
                log.error("Failed to backfill status history for risk item {}: {}",
                        item.getRiskItemId(), e.getMessage());
            }
        }

        log.info("Backfilled status history for {} risk items", backfilledCount);
        return backfilledCount;
    }

    /**
     * Result of migration operation.
     */
    public static class MigrationResult {
        private boolean success;
        private OffsetDateTime startTime;
        private OffsetDateTime endTime;
        private int totalRiskStories;
        private int domainRisksToCreate;
        private int createdDomainRisks;
        private int createdRiskItems;
        private int skippedRiskItems;
        private int failedRiskItems;
        private List<String> failedGroups = new ArrayList<>();
        private String errorMessage;

        // Getters and setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        public OffsetDateTime getStartTime() { return startTime; }
        public void setStartTime(OffsetDateTime startTime) { this.startTime = startTime; }

        public OffsetDateTime getEndTime() { return endTime; }
        public void setEndTime(OffsetDateTime endTime) { this.endTime = endTime; }

        public int getTotalRiskStories() { return totalRiskStories; }
        public void setTotalRiskStories(int totalRiskStories) { this.totalRiskStories = totalRiskStories; }

        public int getDomainRisksToCreate() { return domainRisksToCreate; }
        public void setDomainRisksToCreate(int domainRisksToCreate) { this.domainRisksToCreate = domainRisksToCreate; }

        public int getCreatedDomainRisks() { return createdDomainRisks; }
        public void setCreatedDomainRisks(int createdDomainRisks) { this.createdDomainRisks = createdDomainRisks; }

        public int getCreatedRiskItems() { return createdRiskItems; }
        public void setCreatedRiskItems(int createdRiskItems) { this.createdRiskItems = createdRiskItems; }

        public int getSkippedRiskItems() { return skippedRiskItems; }
        public void setSkippedRiskItems(int skippedRiskItems) { this.skippedRiskItems = skippedRiskItems; }

        public int getFailedRiskItems() { return failedRiskItems; }
        public void setFailedRiskItems(int failedRiskItems) { this.failedRiskItems = failedRiskItems; }

        public List<String> getFailedGroups() { return failedGroups; }
        public void setFailedGroups(List<String> failedGroups) { this.failedGroups = failedGroups; }

        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

        @Override
        public String toString() {
            return String.format(
                "MigrationResult{success=%s, totalRiskStories=%d, createdDomainRisks=%d, " +
                "createdRiskItems=%d, skippedRiskItems=%d, failedRiskItems=%d, failedGroups=%d}",
                success, totalRiskStories, createdDomainRisks, createdRiskItems,
                skippedRiskItems, failedRiskItems, failedGroups.size()
            );
        }
    }
}
