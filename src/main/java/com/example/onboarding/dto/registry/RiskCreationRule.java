package com.example.onboarding.dto.registry;

/**
 * Risk creation rule for a specific criticality level
 */
public record RiskCreationRule(
        String value,           // The field value (e.g., "required", "recommended")  
        String label,           // Display label
        String ttl,             // Time to live
        boolean requiresReview  // Whether this field+criticality combo requires SME review
) {
    
    public static RiskCreationRule fromRegistryRule(String value, String label, String ttl, Boolean requiresReview) {
        return new RiskCreationRule(
            value, 
            label, 
            ttl, 
            requiresReview != null ? requiresReview : false  // Default to false if not specified
        );
    }
}