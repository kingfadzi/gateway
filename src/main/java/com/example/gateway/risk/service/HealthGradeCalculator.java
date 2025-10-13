package com.example.gateway.risk.service;

import org.springframework.stereotype.Component;

/**
 * Calculates health grade based on average risk score.
 * Used for ARB dashboard metrics.
 *
 * Grading scale:
 * - A: averageRiskScore ≤ 20
 * - B: averageRiskScore ≤ 40
 * - C: averageRiskScore ≤ 60
 * - D: averageRiskScore ≤ 80
 * - F: averageRiskScore > 80
 */
@Component
public class HealthGradeCalculator {

    /**
     * Calculate health grade from average risk score.
     *
     * @param averageRiskScore Average priority score (0-100)
     * @return Health grade (A, B, C, D, or F)
     */
    public String calculateHealthGrade(double averageRiskScore) {
        if (averageRiskScore <= 20) {
            return "A";
        } else if (averageRiskScore <= 40) {
            return "B";
        } else if (averageRiskScore <= 60) {
            return "C";
        } else if (averageRiskScore <= 80) {
            return "D";
        } else {
            return "F";
        }
    }

    /**
     * Calculate health grade from average risk score with null handling.
     * Returns "A" if score is null or zero.
     *
     * @param averageRiskScore Average priority score (0-100), may be null
     * @return Health grade (A, B, C, D, or F)
     */
    public String calculateHealthGrade(Double averageRiskScore) {
        if (averageRiskScore == null || averageRiskScore == 0.0) {
            return "A";
        }
        return calculateHealthGrade(averageRiskScore.doubleValue());
    }
}
