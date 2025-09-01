package com.example.onboarding.dto.registry;

/**
 * Result of evaluating registry rules for risk creation
 */
public record RegistryRuleEvaluation(
        String fieldKey,
        String appId,
        String appCriticality,
        boolean shouldCreateRisk,
        RiskCreationRule matchedRule,
        String evaluationReason
) {
    
    public static RegistryRuleEvaluation riskRequired(String fieldKey, String appId, String appCriticality, RiskCreationRule rule) {
        return new RegistryRuleEvaluation(
            fieldKey,
            appId,
            appCriticality,
            true,
            rule,
            String.format("Field %s requires review for criticality %s", fieldKey, appCriticality)
        );
    }
    
    public static RegistryRuleEvaluation noRiskNeeded(String fieldKey, String appId, String appCriticality, RiskCreationRule rule) {
        return new RegistryRuleEvaluation(
            fieldKey,
            appId,
            appCriticality,
            false,
            rule,
            String.format("Field %s does not require review for criticality %s", fieldKey, appCriticality)
        );
    }
    
    public static RegistryRuleEvaluation noRuleFound(String fieldKey, String appId, String appCriticality) {
        return new RegistryRuleEvaluation(
            fieldKey,
            appId,
            appCriticality,
            false,
            null,
            String.format("No registry rule found for field %s with criticality %s", fieldKey, appCriticality)
        );
    }
}