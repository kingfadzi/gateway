package com.example.gateway.registry.service;

import com.example.gateway.registry.dto.RegistryRuleEvaluation;
import com.example.gateway.registry.dto.FieldRiskConfig;
import com.example.gateway.registry.dto.RiskCreationRule;
import com.example.gateway.profile.service.ProfileFieldRegistryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Service for evaluating registry-based risk creation rules
 */
@Service
public class RegistryRiskConfigService {
    
    private static final Logger log = LoggerFactory.getLogger(RegistryRiskConfigService.class);
    
    private final ProfileFieldRegistryService profileFieldRegistryService;
    
    public RegistryRiskConfigService(ProfileFieldRegistryService profileFieldRegistryService) {
        this.profileFieldRegistryService = profileFieldRegistryService;
    }
    
    /**
     * Evaluate if evidence attachment should create a risk
     */
    public RegistryRuleEvaluation evaluateRiskCreation(String fieldKey, String appId, String appCriticality) {
        log.debug("Evaluating risk creation for field={}, appId={}, criticality={}", fieldKey, appId, appCriticality);
        
        Optional<FieldRiskConfig> configOpt = profileFieldRegistryService.getFieldRiskConfig(fieldKey);
        
        if (configOpt.isEmpty()) {
            log.debug("No risk configuration found for field: {}", fieldKey);
            return RegistryRuleEvaluation.noRuleFound(fieldKey, appId, appCriticality);
        }
        
        FieldRiskConfig config = configOpt.get();
        RiskCreationRule rule = config.getRuleForCriticality(appCriticality);
        
        if (rule == null) {
            log.debug("No rule found for field={} with criticality={}", fieldKey, appCriticality);
            return RegistryRuleEvaluation.noRuleFound(fieldKey, appId, appCriticality);
        }
        
        boolean shouldCreate = rule.requiresReview();
        log.info("Risk evaluation: field={}, criticality={}, requiresReview={}, ruleValue={}", 
            fieldKey, appCriticality, shouldCreate, rule.value());
        
        return shouldCreate 
            ? RegistryRuleEvaluation.riskRequired(fieldKey, appId, appCriticality, rule)
            : RegistryRuleEvaluation.noRiskNeeded(fieldKey, appId, appCriticality, rule);
    }
    
    /**
     * Get all fields that require review for any criticality level
     */
    public List<String> getFieldsRequiringReview() {
        return profileFieldRegistryService.getAllRiskConfigs().entrySet().stream()
                .filter(entry -> entry.getValue().rules().values().stream()
                        .anyMatch(RiskCreationRule::requiresReview))
                .map(entry -> entry.getKey())
                .toList();
    }
    
    /**
     * Get all fields that require review for specific criticality
     */
    public List<String> getFieldsRequiringReviewForCriticality(String criticality) {
        return profileFieldRegistryService.getAllRiskConfigs().entrySet().stream()
                .filter(entry -> {
                    RiskCreationRule rule = entry.getValue().getRuleForCriticality(criticality);
                    return rule != null && rule.requiresReview();
                })
                .map(entry -> entry.getKey())
                .toList();
    }
    
    /**
     * Check if any field requires review for the given criticality
     */
    public boolean hasFieldsRequiringReview(String criticality) {
        return profileFieldRegistryService.getAllRiskConfigs().values().stream()
                .anyMatch(config -> {
                    RiskCreationRule rule = config.getRuleForCriticality(criticality);
                    return rule != null && rule.requiresReview();
                });
    }
    
    /**
     * Get risk configuration summary for admin purposes
     */
    public String getRiskConfigurationSummary() {
        var configs = profileFieldRegistryService.getAllRiskConfigs();
        long totalFields = configs.size();
        long fieldsWithReviewRequired = getFieldsRequiringReview().size();
        
        return String.format("Risk Configuration: %d total fields, %d require review", 
                totalFields, fieldsWithReviewRequired);
    }
}