package com.example.gateway.registry.dto;

import com.example.gateway.risk.model.RiskPriority;

/**
 * Risk creation rule for a specific criticality level
 */
public record RiskCreationRule(
        String value,           // The field value (e.g., "required", "recommended")
        String label,           // Display label
        String ttl,             // Time to live
        boolean requiresReview, // Whether this field+criticality combo requires SME review
        RiskPriority priority   // Priority level (CRITICAL, HIGH, MEDIUM, LOW)
) {

    public static RiskCreationRule fromRegistryRule(String value, String label, String ttl, Boolean requiresReview, String priority) {
        return new RiskCreationRule(
            value,
            label,
            ttl,
            requiresReview != null ? requiresReview : false,  // Default to false if not specified
            parsePriority(priority)  // Parse priority string to enum
        );
    }

    /**
     * Parse priority string to RiskPriority enum, default to LOW if not specified
     */
    private static RiskPriority parsePriority(String priority) {
        if (priority == null || priority.isEmpty()) {
            return RiskPriority.LOW;  // Default to LOW if not specified
        }

        try {
            return RiskPriority.valueOf(priority.toUpperCase());
        } catch (IllegalArgumentException e) {
            return RiskPriority.LOW;  // Default to LOW if invalid
        }
    }
}