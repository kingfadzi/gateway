package com.example.gateway.risk.service;

import com.example.gateway.risk.model.*;
import com.example.gateway.risk.repository.DomainRiskRepository;
import com.example.gateway.risk.repository.RiskItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for DomainRiskAggregationService.
 *
 * Tests the complete risk aggregation flow including:
 * - Domain risk creation
 * - Risk item addition
 * - Automatic aggregation calculations
 * - Status transitions
 * - ARB routing
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("DomainRiskAggregationService Integration Tests")
class DomainRiskAggregationServiceIntegrationTest {

    @Autowired
    private DomainRiskAggregationService aggregationService;

    @Autowired
    private DomainRiskRepository domainRiskRepository;

    @Autowired
    private RiskItemRepository riskItemRepository;

    @Autowired
    private RiskPriorityCalculator priorityCalculator;

    private static final String TEST_APP_ID = "TEST-APP-001";
    private static final String TEST_DERIVED_FROM = "security_rating";

    @BeforeEach
    void setUp() {
        // Clean up test data
        riskItemRepository.deleteAll();
        domainRiskRepository.deleteAll();
    }

    @Test
    @DisplayName("should create new domain risk for app and domain combination")
    void createNewDomainRisk() {
        // Act
        DomainRisk domainRisk = aggregationService.getOrCreateDomainRisk(TEST_APP_ID, TEST_DERIVED_FROM);

        // Assert
        assertThat(domainRisk).isNotNull();
        assertThat(domainRisk.getDomainRiskId()).isNotNull();
        assertThat(domainRisk.getAppId()).isEqualTo(TEST_APP_ID);
        assertThat(domainRisk.getRiskRatingDimension()).isEqualTo(TEST_DERIVED_FROM);
        assertThat(domainRisk.getDerivedFrom()).isEqualTo(TEST_DERIVED_FROM);
        assertThat(domainRisk.getArb()).isIn("security", "data", "operations", "enterprise_architecture");
        assertThat(domainRisk.getStatus()).isEqualTo(DomainRiskStatus.PENDING_ARB_REVIEW);
        assertThat(domainRisk.getTotalItems()).isEqualTo(0);
        assertThat(domainRisk.getOpenItems()).isEqualTo(0);
        assertThat(domainRisk.getHighPriorityItems()).isEqualTo(0);
    }

    @Test
    @DisplayName("should return existing domain risk when called twice for same app and domain")
    void getExistingDomainRisk() {
        // Arrange
        DomainRisk first = aggregationService.getOrCreateDomainRisk(TEST_APP_ID, TEST_DERIVED_FROM);
        String firstId = first.getDomainRiskId();

        // Act
        DomainRisk second = aggregationService.getOrCreateDomainRisk(TEST_APP_ID, TEST_DERIVED_FROM);

        // Assert
        assertThat(second.getDomainRiskId()).isEqualTo(firstId);
    }

    @Test
    @DisplayName("should add risk item to domain risk and update aggregations")
    void addRiskItemToDomainRisk() {
        // Arrange
        DomainRisk domainRisk = aggregationService.getOrCreateDomainRisk(TEST_APP_ID, TEST_DERIVED_FROM);

        RiskItem riskItem = createTestRiskItem(
            domainRisk.getDomainRiskId(),
            "encryption_at_rest",
            RiskPriority.CRITICAL,
            "missing",
            75
        );

        // Act
        aggregationService.addRiskItemToDomain(domainRisk, riskItem);

        // Assert - Risk item saved
        RiskItem savedItem = riskItemRepository.findById(riskItem.getRiskItemId()).orElseThrow();
        assertThat(savedItem.getDomainRiskId()).isEqualTo(domainRisk.getDomainRiskId());

        // Assert - Domain risk updated
        DomainRisk updatedDomainRisk = domainRiskRepository.findById(domainRisk.getDomainRiskId()).orElseThrow();
        assertThat(updatedDomainRisk.getTotalItems()).isEqualTo(1);
        assertThat(updatedDomainRisk.getOpenItems()).isEqualTo(1);
        assertThat(updatedDomainRisk.getHighPriorityItems()).isGreaterThan(0);
        assertThat(updatedDomainRisk.getLastItemAddedAt()).isNotNull();
    }

    @Test
    @DisplayName("should recalculate aggregations when multiple items added")
    void recalculateAggregationsWithMultipleItems() {
        // Arrange
        DomainRisk domainRisk = aggregationService.getOrCreateDomainRisk(TEST_APP_ID, TEST_DERIVED_FROM);

        // Add 3 risk items with different priorities
        addTestRiskItem(domainRisk, "item1", RiskPriority.CRITICAL, "missing", 100);
        addTestRiskItem(domainRisk, "item2", RiskPriority.HIGH, "expired", 60);
        addTestRiskItem(domainRisk, "item3", RiskPriority.MEDIUM, "approved", 20);

        // Act
        aggregationService.recalculateAggregations(domainRisk);

        // Assert
        DomainRisk updated = domainRiskRepository.findById(domainRisk.getDomainRiskId()).orElseThrow();
        assertThat(updated.getTotalItems()).isEqualTo(3);
        assertThat(updated.getOpenItems()).isEqualTo(3);
        assertThat(updated.getHighPriorityItems()).isGreaterThan(0);
        assertThat(updated.getPriorityScore()).isGreaterThanOrEqualTo(100); // Max score + bonuses
        assertThat(updated.getOverallPriority()).isNotNull();
        assertThat(updated.getOverallSeverity()).isNotNull();
    }

    @Test
    @DisplayName("should auto-transition to RESOLVED when all items closed")
    void autoTransitionToResolved() {
        // Arrange
        DomainRisk domainRisk = aggregationService.getOrCreateDomainRisk(TEST_APP_ID, TEST_DERIVED_FROM);
        RiskItem item1 = addTestRiskItem(domainRisk, "item1", RiskPriority.HIGH, "missing", 75);
        RiskItem item2 = addTestRiskItem(domainRisk, "item2", RiskPriority.MEDIUM, "missing", 50);

        // Act - Close all items
        aggregationService.updateRiskItemStatus(
            item1.getRiskItemId(),
            RiskItemStatus.RESOLVED,
            "evidence_provided",
            "Evidence uploaded"
        );
        aggregationService.updateRiskItemStatus(
            item2.getRiskItemId(),
            RiskItemStatus.RESOLVED,
            "evidence_provided",
            "Evidence uploaded"
        );

        // Assert
        DomainRisk updated = domainRiskRepository.findById(domainRisk.getDomainRiskId()).orElseThrow();
        assertThat(updated.getOpenItems()).isEqualTo(0);
        assertThat(updated.getStatus()).isEqualTo(DomainRiskStatus.RESOLVED);
    }

    @Test
    @DisplayName("should reopen domain risk when new item added to resolved risk")
    void reopenResolvedDomainRisk() {
        // Arrange
        DomainRisk domainRisk = aggregationService.getOrCreateDomainRisk(TEST_APP_ID, TEST_DERIVED_FROM);
        RiskItem item1 = addTestRiskItem(domainRisk, "item1", RiskPriority.HIGH, "missing", 75);

        // Close item
        aggregationService.updateRiskItemStatus(
            item1.getRiskItemId(),
            RiskItemStatus.RESOLVED,
            "evidence_provided",
            null
        );

        // Verify resolved
        DomainRisk resolved = domainRiskRepository.findById(domainRisk.getDomainRiskId()).orElseThrow();
        assertThat(resolved.getStatus()).isEqualTo(DomainRiskStatus.RESOLVED);

        // Act - Add new item
        addTestRiskItem(domainRisk, "item2", RiskPriority.CRITICAL, "missing", 100);

        // Assert - Domain risk reopened
        DomainRisk reopened = domainRiskRepository.findById(domainRisk.getDomainRiskId()).orElseThrow();
        assertThat(reopened.getStatus()).isEqualTo(DomainRiskStatus.IN_PROGRESS);
        assertThat(reopened.getOpenItems()).isEqualTo(1);
    }

    @Test
    @DisplayName("should calculate correct priority scores with evidence multipliers")
    void calculatePriorityScoresWithMultipliers() {
        // Arrange
        DomainRisk domainRisk = aggregationService.getOrCreateDomainRisk(TEST_APP_ID, TEST_DERIVED_FROM);

        // Add items with different evidence statuses
        addTestRiskItem(domainRisk, "missing_critical", RiskPriority.CRITICAL, "missing", 100);
        addTestRiskItem(domainRisk, "expired_high", RiskPriority.HIGH, "expired", 60);
        addTestRiskItem(domainRisk, "approved_high", RiskPriority.HIGH, "approved", 30);

        // Act
        aggregationService.recalculateAggregations(domainRisk);

        // Assert - Domain score should reflect highest item + bonuses
        DomainRisk updated = domainRiskRepository.findById(domainRisk.getDomainRiskId()).orElseThrow();
        assertThat(updated.getPriorityScore()).isGreaterThanOrEqualTo(100);
        assertThat(updated.getOverallPriority()).isIn("CRITICAL", "HIGH");
    }

    @Test
    @DisplayName("should handle manual risk creation")
    void createManualRisk() {
        // Act
        RiskItem manualRisk = aggregationService.createManualRisk(
            TEST_APP_ID,
            "manual_field_key",
            null, // No profile field ID
            "Manual Risk Title",
            "This is a manually created risk for testing",
            "Hypothesis: Missing security controls",  // hypothesis
            "If controls not implemented",            // condition
            "Security breach may occur",              // consequence
            "CTRL-SEC-001, CTRL-SEC-002",            // controlRefs
            RiskPriority.HIGH,
            "test_user",
            null, // No evidence ID
            TEST_DERIVED_FROM
        );

        // Assert - Risk item created
        assertThat(manualRisk).isNotNull();
        assertThat(manualRisk.getRiskItemId()).startsWith("item_");
        assertThat(manualRisk.getTitle()).isEqualTo("Manual Risk Title");
        assertThat(manualRisk.getCreationType()).isEqualTo(RiskCreationType.MANUAL_CREATION);
        assertThat(manualRisk.getRaisedBy()).isEqualTo("test_user");
        assertThat(manualRisk.getStatus()).isEqualTo(RiskItemStatus.OPEN);

        // Assert - Domain risk created and linked
        assertThat(manualRisk.getDomainRiskId()).isNotNull();
        DomainRisk domainRisk = domainRiskRepository.findById(manualRisk.getDomainRiskId()).orElseThrow();
        assertThat(domainRisk.getTotalItems()).isEqualTo(1);
    }

    @Test
    @DisplayName("should reassign domain risk to different ARB")
    void reassignDomainRisk() {
        // Arrange
        DomainRisk domainRisk = aggregationService.getOrCreateDomainRisk(TEST_APP_ID, TEST_DERIVED_FROM);
        String originalArb = domainRisk.getAssignedArb();

        // Act
        DomainRisk reassigned = aggregationService.reassignDomainRisk(
            domainRisk.getDomainRiskId(),
            "operations",
            "admin_user"
        );

        // Assert
        assertThat(reassigned.getAssignedArb()).isEqualTo("operations");
        assertThat(reassigned.getAssignedArb()).isNotEqualTo(originalArb);
        assertThat(reassigned.getAssignedAt()).isNotNull();
    }

    @Test
    @DisplayName("should retrieve domain risks for specific ARB")
    void getDomainRisksForArb() {
        // Arrange - Create risks for different ARBs
        DomainRisk security1 = aggregationService.getOrCreateDomainRisk("APP-001", "security_rating");
        DomainRisk security2 = aggregationService.getOrCreateDomainRisk("APP-002", "security_rating");
        DomainRisk integrity = aggregationService.getOrCreateDomainRisk("APP-003", "integrity_rating");

        addTestRiskItem(security1, "item1", RiskPriority.HIGH, "missing", 75);
        addTestRiskItem(security2, "item2", RiskPriority.CRITICAL, "missing", 100);

        // Act
        List<DomainRisk> securityRisks = aggregationService.getDomainRisksForArb(
            security1.getArb(),
            List.of(DomainRiskStatus.PENDING_ARB_REVIEW, DomainRiskStatus.IN_PROGRESS)
        );

        // Assert
        assertThat(securityRisks).hasSizeGreaterThanOrEqualTo(2);
        assertThat(securityRisks).allMatch(dr -> dr.getArb().equals(security1.getArb()));
    }

    @Test
    @DisplayName("should retrieve all risk items for an app ordered by priority")
    void getRiskItemsForApp() {
        // Arrange
        DomainRisk domainRisk = aggregationService.getOrCreateDomainRisk(TEST_APP_ID, TEST_DERIVED_FROM);

        RiskItem critical = addTestRiskItem(domainRisk, "critical", RiskPriority.CRITICAL, "missing", 100);
        RiskItem high = addTestRiskItem(domainRisk, "high", RiskPriority.HIGH, "expired", 60);
        RiskItem medium = addTestRiskItem(domainRisk, "medium", RiskPriority.MEDIUM, "approved", 20);

        // Act
        List<RiskItem> items = aggregationService.getRiskItemsForApp(TEST_APP_ID);

        // Assert - Should be ordered by priority score DESC
        assertThat(items).hasSize(3);
        assertThat(items.get(0).getPriorityScore()).isGreaterThanOrEqualTo(items.get(1).getPriorityScore());
        assertThat(items.get(1).getPriorityScore()).isGreaterThanOrEqualTo(items.get(2).getPriorityScore());
    }

    @Test
    @DisplayName("should retrieve risk items for specific domain")
    void getRiskItemsForDomain() {
        // Arrange
        DomainRisk domainRisk = aggregationService.getOrCreateDomainRisk(TEST_APP_ID, TEST_DERIVED_FROM);
        addTestRiskItem(domainRisk, "item1", RiskPriority.HIGH, "missing", 75);
        addTestRiskItem(domainRisk, "item2", RiskPriority.MEDIUM, "expired", 40);

        // Act
        List<RiskItem> items = aggregationService.getRiskItemsForDomain(domainRisk.getDomainRiskId());

        // Assert
        assertThat(items).hasSize(2);
        assertThat(items).allMatch(item -> item.getDomainRiskId().equals(domainRisk.getDomainRiskId()));
    }

    @Test
    @DisplayName("should handle complete end-to-end workflow")
    void completeEndToEndWorkflow() {
        // 1. Create domain risk
        DomainRisk domainRisk = aggregationService.getOrCreateDomainRisk(TEST_APP_ID, TEST_DERIVED_FROM);
        assertThat(domainRisk.getStatus()).isEqualTo(DomainRiskStatus.PENDING_ARB_REVIEW);

        // 2. Add multiple risk items
        RiskItem item1 = addTestRiskItem(domainRisk, "item1", RiskPriority.CRITICAL, "missing", 100);
        RiskItem item2 = addTestRiskItem(domainRisk, "item2", RiskPriority.HIGH, "non_compliant", 69);
        RiskItem item3 = addTestRiskItem(domainRisk, "item3", RiskPriority.MEDIUM, "under_review", 30);

        // 3. Verify aggregations
        DomainRisk afterAdd = domainRiskRepository.findById(domainRisk.getDomainRiskId()).orElseThrow();
        assertThat(afterAdd.getTotalItems()).isEqualTo(3);
        assertThat(afterAdd.getOpenItems()).isEqualTo(3);
        assertThat(afterAdd.getPriorityScore()).isGreaterThanOrEqualTo(100); // Max + bonuses

        // 4. Resolve some items
        aggregationService.updateRiskItemStatus(item3.getRiskItemId(), RiskItemStatus.RESOLVED, null, null);

        DomainRisk afterResolve = domainRiskRepository.findById(domainRisk.getDomainRiskId()).orElseThrow();
        assertThat(afterResolve.getOpenItems()).isEqualTo(2);

        // 5. Resolve all remaining items
        aggregationService.updateRiskItemStatus(item1.getRiskItemId(), RiskItemStatus.RESOLVED, null, null);
        aggregationService.updateRiskItemStatus(item2.getRiskItemId(), RiskItemStatus.WAIVED, "accepted_risk", null);

        // 6. Verify auto-transition to RESOLVED
        DomainRisk finalDomainRisk = domainRiskRepository.findById(domainRisk.getDomainRiskId()).orElseThrow();
        assertThat(finalDomainRisk.getOpenItems()).isEqualTo(0);
        assertThat(finalDomainRisk.getStatus()).isEqualTo(DomainRiskStatus.RESOLVED);

        // 7. Add new item to resolved risk
        addTestRiskItem(finalDomainRisk, "item4", RiskPriority.HIGH, "missing", 75);

        // 8. Verify reopened
        DomainRisk reopened = domainRiskRepository.findById(domainRisk.getDomainRiskId()).orElseThrow();
        assertThat(reopened.getStatus()).isEqualTo(DomainRiskStatus.IN_PROGRESS);
        assertThat(reopened.getTotalItems()).isEqualTo(4);
        assertThat(reopened.getOpenItems()).isEqualTo(1);
    }

    // ========== Helper Methods ==========

    private RiskItem createTestRiskItem(
        String domainRiskId,
        String fieldKey,
        RiskPriority priority,
        String evidenceStatus,
        int priorityScore
    ) {
        RiskItem item = new RiskItem();
        item.setRiskItemId("test_item_" + System.nanoTime());
        item.setDomainRiskId(domainRiskId);
        item.setAppId(TEST_APP_ID);
        item.setFieldKey(fieldKey);
        item.setTitle("Test Risk: " + fieldKey);
        item.setDescription("Test risk for integration testing");
        item.setPriority(priority);
        item.setEvidenceStatus(evidenceStatus);
        item.setPriorityScore(priorityScore);
        item.setSeverity(priorityCalculator.getSeverityLabel(priorityScore));
        item.setStatus(RiskItemStatus.OPEN);
        item.setCreationType(RiskCreationType.SYSTEM_AUTO_CREATION);
        item.setOpenedAt(OffsetDateTime.now());
        return item;
    }

    private RiskItem addTestRiskItem(
        DomainRisk domainRisk,
        String fieldKey,
        RiskPriority priority,
        String evidenceStatus,
        int priorityScore
    ) {
        RiskItem item = createTestRiskItem(domainRisk.getDomainRiskId(), fieldKey, priority, evidenceStatus, priorityScore);
        aggregationService.addRiskItemToDomain(domainRisk, item);
        return item;
    }
}
