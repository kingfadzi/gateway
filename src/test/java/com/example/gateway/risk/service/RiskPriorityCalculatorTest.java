package com.example.gateway.risk.service;

import com.example.gateway.risk.model.RiskPriority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for RiskPriorityCalculator service.
 *
 * Tests priority score calculation logic including:
 * - Base priority scores
 * - Evidence status multipliers
 * - Domain-level aggregation
 * - Score-to-priority mapping
 * - Severity labels
 */
@DisplayName("RiskPriorityCalculator")
class RiskPriorityCalculatorTest {

    private RiskPriorityCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new RiskPriorityCalculator();
    }

    @Nested
    @DisplayName("calculatePriorityScore")
    class CalculatePriorityScore {

        @Test
        @DisplayName("should calculate CRITICAL priority with missing evidence as 100 (40 * 2.5)")
        void criticalWithMissing() {
            int score = calculator.calculatePriorityScore(RiskPriority.CRITICAL, "missing");
            assertThat(score).isEqualTo(100);  // 40 * 2.5 = 100
        }

        @Test
        @DisplayName("should calculate HIGH priority with missing evidence as 75 (30 * 2.5)")
        void highWithMissing() {
            int score = calculator.calculatePriorityScore(RiskPriority.HIGH, "missing");
            assertThat(score).isEqualTo(75);  // 30 * 2.5 = 75
        }

        @Test
        @DisplayName("should calculate MEDIUM priority with missing evidence as 50 (20 * 2.5)")
        void mediumWithMissing() {
            int score = calculator.calculatePriorityScore(RiskPriority.MEDIUM, "missing");
            assertThat(score).isEqualTo(50);  // 20 * 2.5 = 50
        }

        @Test
        @DisplayName("should calculate LOW priority with missing evidence as 25 (10 * 2.5)")
        void lowWithMissing() {
            int score = calculator.calculatePriorityScore(RiskPriority.LOW, "missing");
            assertThat(score).isEqualTo(25);  // 10 * 2.5 = 25
        }

        @Test
        @DisplayName("should calculate CRITICAL priority with non_compliant evidence as 92 (40 * 2.3)")
        void criticalWithNonCompliant() {
            int score = calculator.calculatePriorityScore(RiskPriority.CRITICAL, "non_compliant");
            assertThat(score).isEqualTo(92);  // 40 * 2.3 = 92
        }

        @Test
        @DisplayName("should calculate CRITICAL priority with expired evidence as 80 (40 * 2.0)")
        void criticalWithExpired() {
            int score = calculator.calculatePriorityScore(RiskPriority.CRITICAL, "expired");
            assertThat(score).isEqualTo(80);  // 40 * 2.0 = 80
        }

        @Test
        @DisplayName("should calculate CRITICAL priority with approved evidence as 40 (40 * 1.0)")
        void criticalWithApproved() {
            int score = calculator.calculatePriorityScore(RiskPriority.CRITICAL, "approved");
            assertThat(score).isEqualTo(40);  // 40 * 1.0 = 40 (base score)
        }

        @Test
        @DisplayName("should calculate MEDIUM priority with approved evidence as 20 (20 * 1.0)")
        void mediumWithApproved() {
            int score = calculator.calculatePriorityScore(RiskPriority.MEDIUM, "approved");
            assertThat(score).isEqualTo(20);  // 20 * 1.0 = 20 (base score)
        }

        @Test
        @DisplayName("should calculate LOW priority with waived evidence as 5 (10 * 0.5)")
        void lowWithWaived() {
            int score = calculator.calculatePriorityScore(RiskPriority.LOW, "waived");
            assertThat(score).isEqualTo(5);  // 10 * 0.5 = 5
        }

        @Test
        @DisplayName("should handle null priority by defaulting to LOW")
        void nullPriority() {
            int score = calculator.calculatePriorityScore(null, "missing");
            assertThat(score).isEqualTo(25);  // LOW (10) * missing (2.5) = 25
        }

        @Test
        @DisplayName("should handle null evidence status with 2.0 multiplier")
        void nullEvidenceStatus() {
            int score = calculator.calculatePriorityScore(RiskPriority.CRITICAL, null);
            assertThat(score).isEqualTo(80);  // 40 * 2.0 = 80
        }

        @Test
        @DisplayName("should handle empty evidence status with 2.0 multiplier")
        void emptyEvidenceStatus() {
            int score = calculator.calculatePriorityScore(RiskPriority.CRITICAL, "");
            assertThat(score).isEqualTo(80);  // 40 * 2.0 = 80
        }

        @Test
        @DisplayName("should cap score at 100")
        void capAt100() {
            // Even if calculation would exceed 100, cap it
            int score = calculator.calculatePriorityScore(RiskPriority.CRITICAL, "missing");
            assertThat(score).isLessThanOrEqualTo(100);
        }

        @ParameterizedTest
        @CsvSource({
            "missing, 2.5",
            "not_provided, 2.5",
            "non_compliant, 2.3",
            "failed, 2.3",
            "expired, 2.0",
            "under_review, 1.5",
            "pending, 1.5",
            "needs_update, 1.3",
            "approved, 1.0",
            "compliant, 1.0",
            "waived, 0.5",
            "exempted, 0.5"
        })
        @DisplayName("should apply correct multipliers for various evidence statuses")
        void variousEvidenceStatuses(String status, double expectedMultiplier) {
            int score = calculator.calculatePriorityScore(RiskPriority.HIGH, status);
            int expectedScore = (int) Math.min(100, 30 * expectedMultiplier);
            assertThat(score).isEqualTo(expectedScore);
        }

        @Test
        @DisplayName("should handle case-insensitive evidence status")
        void caseInsensitiveStatus() {
            int score1 = calculator.calculatePriorityScore(RiskPriority.HIGH, "MISSING");
            int score2 = calculator.calculatePriorityScore(RiskPriority.HIGH, "missing");
            int score3 = calculator.calculatePriorityScore(RiskPriority.HIGH, "Missing");

            assertThat(score1).isEqualTo(score2).isEqualTo(score3);
        }

        @Test
        @DisplayName("should use default multiplier for unknown evidence status")
        void unknownEvidenceStatus() {
            int score = calculator.calculatePriorityScore(RiskPriority.MEDIUM, "unknown_status");
            assertThat(score).isEqualTo(30);  // 20 * 1.5 (default multiplier) = 30
        }
    }

    @Nested
    @DisplayName("calculateDomainPriorityScore")
    class CalculateDomainPriorityScore {

        @Test
        @DisplayName("should use max item score as base")
        void baseScore() {
            int score = calculator.calculateDomainPriorityScore(75, 0, 1);
            assertThat(score).isEqualTo(75);
        }

        @Test
        @DisplayName("should add bonus for high-priority items")
        void highPriorityBonus() {
            // 3 high-priority items = +6 bonus (3 * 2 = 6)
            // 5 open items (5-3=2) = +2 volume bonus
            int score = calculator.calculateDomainPriorityScore(70, 3, 5);
            assertThat(score).isEqualTo(78);  // 70 + 6 + 2 = 78
        }

        @Test
        @DisplayName("should cap high-priority bonus at +10")
        void highPriorityBonusCap() {
            // 10 high-priority items = +10 bonus (capped)
            int score = calculator.calculateDomainPriorityScore(80, 10, 15);
            assertThat(score).isEqualTo(95);  // 80 + 10 (capped) + 5 (volume) = 95
        }

        @Test
        @DisplayName("should add bonus for high volume of open items")
        void volumeBonus() {
            // 7 open items (7-3=4) = +4 bonus
            int score = calculator.calculateDomainPriorityScore(60, 0, 7);
            assertThat(score).isEqualTo(64);  // 60 + 4 = 64
        }

        @Test
        @DisplayName("should not add volume bonus for 3 or fewer items")
        void noVolumeBonusForFewItems() {
            int score1 = calculator.calculateDomainPriorityScore(50, 0, 1);
            int score2 = calculator.calculateDomainPriorityScore(50, 0, 2);
            int score3 = calculator.calculateDomainPriorityScore(50, 0, 3);

            assertThat(score1).isEqualTo(50);
            assertThat(score2).isEqualTo(50);
            assertThat(score3).isEqualTo(50);
        }

        @Test
        @DisplayName("should cap volume bonus at +5")
        void volumeBonusCap() {
            // 20 open items (20-3=17) = +5 bonus (capped)
            int score = calculator.calculateDomainPriorityScore(70, 0, 20);
            assertThat(score).isEqualTo(75);  // 70 + 5 (capped) = 75
        }

        @Test
        @DisplayName("should combine high-priority and volume bonuses")
        void combinedBonuses() {
            // 5 high-priority items = +10 (capped)
            // 10 open items (10-3=7) = +5 (capped)
            int score = calculator.calculateDomainPriorityScore(80, 5, 10);
            assertThat(score).isEqualTo(95);  // 80 + 10 + 5 = 95
        }

        @Test
        @DisplayName("should cap total score at 100")
        void capAt100() {
            int score = calculator.calculateDomainPriorityScore(95, 10, 20);
            assertThat(score).isEqualTo(100);  // Capped at 100
        }

        @Test
        @DisplayName("should handle score of 0")
        void zeroScore() {
            int score = calculator.calculateDomainPriorityScore(0, 0, 0);
            assertThat(score).isEqualTo(0);
        }

        @Test
        @DisplayName("should calculate realistic scenario 1: high severity with many items")
        void realisticScenario1() {
            // Max item score: 85 (HIGH priority with non-compliant evidence)
            // 4 high-priority items: +8
            // 12 open items: +5 (capped)
            int score = calculator.calculateDomainPriorityScore(85, 4, 12);
            assertThat(score).isEqualTo(98);  // 85 + 8 + 5 = 98
        }

        @Test
        @DisplayName("should calculate realistic scenario 2: medium severity with few items")
        void realisticScenario2() {
            // Max item score: 50 (MEDIUM priority with missing evidence)
            // 1 high-priority item: +2
            // 2 open items: no volume bonus
            int score = calculator.calculateDomainPriorityScore(50, 1, 2);
            assertThat(score).isEqualTo(52);  // 50 + 2 = 52
        }
    }

    @Nested
    @DisplayName("getPriorityFromScore")
    class GetPriorityFromScore {

        @ParameterizedTest
        @CsvSource({
            "100, CRITICAL",
            "95, CRITICAL",
            "90, CRITICAL",
            "89, HIGH",
            "85, HIGH",
            "70, HIGH",
            "69, MEDIUM",
            "55, MEDIUM",
            "40, MEDIUM",
            "39, LOW",
            "20, LOW",
            "0, LOW"
        })
        @DisplayName("should map scores to correct priority levels")
        void scoreToPriority(int score, RiskPriority expectedPriority) {
            RiskPriority priority = calculator.getPriorityFromScore(score);
            assertThat(priority).isEqualTo(expectedPriority);
        }
    }

    @Nested
    @DisplayName("getSeverityLabel")
    class GetSeverityLabel {

        @ParameterizedTest
        @CsvSource({
            "100, critical",
            "95, critical",
            "90, critical",
            "89, high",
            "75, high",
            "70, high",
            "69, medium",
            "50, medium",
            "40, medium",
            "39, low",
            "20, low",
            "0, low"
        })
        @DisplayName("should return correct severity labels")
        void severityLabels(int score, String expectedLabel) {
            String label = calculator.getSeverityLabel(score);
            assertThat(label).isEqualTo(expectedLabel);
        }
    }

    @Nested
    @DisplayName("requiresImmediateAttention")
    class RequiresImmediateAttention {

        @Test
        @DisplayName("should return true for CRITICAL scores (>= 90)")
        void criticalRequiresAttention() {
            assertThat(calculator.requiresImmediateAttention(100)).isTrue();
            assertThat(calculator.requiresImmediateAttention(95)).isTrue();
            assertThat(calculator.requiresImmediateAttention(90)).isTrue();
        }

        @Test
        @DisplayName("should return true for HIGH scores (70-89)")
        void highRequiresAttention() {
            assertThat(calculator.requiresImmediateAttention(89)).isTrue();
            assertThat(calculator.requiresImmediateAttention(75)).isTrue();
            assertThat(calculator.requiresImmediateAttention(70)).isTrue();
        }

        @Test
        @DisplayName("should return false for MEDIUM scores (40-69)")
        void mediumDoesNotRequireImmediateAttention() {
            assertThat(calculator.requiresImmediateAttention(69)).isFalse();
            assertThat(calculator.requiresImmediateAttention(50)).isFalse();
            assertThat(calculator.requiresImmediateAttention(40)).isFalse();
        }

        @Test
        @DisplayName("should return false for LOW scores (0-39)")
        void lowDoesNotRequireImmediateAttention() {
            assertThat(calculator.requiresImmediateAttention(39)).isFalse();
            assertThat(calculator.requiresImmediateAttention(20)).isFalse();
            assertThat(calculator.requiresImmediateAttention(0)).isFalse();
        }
    }

    @Nested
    @DisplayName("Integration scenarios")
    class IntegrationScenarios {

        @Test
        @DisplayName("should handle complete workflow: calculate -> classify -> label")
        void completeWorkflow() {
            // Calculate score
            int score = calculator.calculatePriorityScore(RiskPriority.CRITICAL, "missing");
            assertThat(score).isEqualTo(100);

            // Get priority level
            RiskPriority priority = calculator.getPriorityFromScore(score);
            assertThat(priority).isEqualTo(RiskPriority.CRITICAL);

            // Get severity label
            String label = calculator.getSeverityLabel(score);
            assertThat(label).isEqualTo("critical");

            // Check if requires attention
            boolean requiresAttention = calculator.requiresImmediateAttention(score);
            assertThat(requiresAttention).isTrue();
        }

        @Test
        @DisplayName("should handle complete domain workflow")
        void completeDomainWorkflow() {
            // Calculate individual item scores
            int item1 = calculator.calculatePriorityScore(RiskPriority.HIGH, "missing");  // 75
            int item2 = calculator.calculatePriorityScore(RiskPriority.CRITICAL, "approved");  // 40
            int item3 = calculator.calculatePriorityScore(RiskPriority.MEDIUM, "expired");  // 40

            // Calculate domain score (using max of 75, with 2 high-priority items, 3 total)
            int domainScore = calculator.calculateDomainPriorityScore(75, 2, 3);
            assertThat(domainScore).isEqualTo(79);  // 75 + 4 (2 high-priority) = 79

            // Classify domain
            RiskPriority domainPriority = calculator.getPriorityFromScore(domainScore);
            assertThat(domainPriority).isEqualTo(RiskPriority.HIGH);

            String severity = calculator.getSeverityLabel(domainScore);
            assertThat(severity).isEqualTo("high");
        }
    }
}
