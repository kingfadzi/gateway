package com.example.gateway.registry.dto;

import java.util.Map;

/**
 * Risk configuration for a profile field from the registry
 */
public record FieldRiskConfig(
        String fieldKey,
        String label,
        String derivedFrom,
        Map<String, RiskCreationRule> rules  // Criticality -> Rule mapping
) {
    
    /**
     * Check if field requires review for given app criticality
     */
    public boolean requiresReviewForCriticality(String criticality) {
        RiskCreationRule rule = rules.get(criticality);
        return rule != null && rule.requiresReview();
    }
    
    /**
     * Get risk creation rule for given app criticality
     */
    public RiskCreationRule getRuleForCriticality(String criticality) {
        return rules.get(criticality);
    }
}