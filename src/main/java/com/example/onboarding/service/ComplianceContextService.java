package com.example.onboarding.service;

import com.example.onboarding.dto.profile.ProfileFieldTypeInfo;
import com.example.onboarding.dto.registry.ComplianceFramework;
import com.example.onboarding.dto.registry.FieldRiskConfig;
import com.example.onboarding.dto.registry.RiskCreationRule;
import com.example.onboarding.service.profile.ProfileFieldRegistryService;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ComplianceContextService {
    
    private final ProfileFieldRegistryService profileFieldRegistryService;
    
    public ComplianceContextService(ProfileFieldRegistryService profileFieldRegistryService) {
        this.profileFieldRegistryService = profileFieldRegistryService;
    }
    
    /**
     * Get compliance context for a field key as a snapshot for risk stories
     */
    public Map<String, Object> getComplianceSnapshot(String fieldKey) {
        return getComplianceSnapshot(fieldKey, null);
    }
    
    /**
     * Get compliance context for a field key with specific app rating as a snapshot for risk stories
     */
    public Map<String, Object> getComplianceSnapshot(String fieldKey, String appRating) {
        Map<String, Object> snapshot = new HashMap<>();
        
        Optional<ProfileFieldTypeInfo> fieldInfo = profileFieldRegistryService.getFieldTypeInfo(fieldKey);
        if (fieldInfo.isPresent()) {
            ProfileFieldTypeInfo field = fieldInfo.get();
            
            snapshot.put("fieldKey", field.fieldKey());
            snapshot.put("fieldLabel", field.label());
            snapshot.put("complianceFrameworks", field.complianceFrameworks());
            snapshot.put("snapshotTimestamp", System.currentTimeMillis());
            
            // Include rule information if app rating is provided
            if (appRating != null) {
                Optional<FieldRiskConfig> riskConfig = profileFieldRegistryService.getFieldRiskConfig(fieldKey);
                if (riskConfig.isPresent()) {
                    RiskCreationRule rule = riskConfig.get().getRuleForCriticality(appRating);
                    if (rule != null) {
                        Map<String, Object> ruleSnapshot = new HashMap<>();
                        // Use the actual derived_from field name instead of generic "appRating"
                        ruleSnapshot.put(field.derivedFrom(), appRating);
                        ruleSnapshot.put("value", rule.value());
                        ruleSnapshot.put("label", rule.label());
                        ruleSnapshot.put("ttl", rule.ttl());
                        ruleSnapshot.put("requiresReview", rule.requiresReview());
                        snapshot.put("activeRule", ruleSnapshot);
                    }
                }
            }
        }
        
        return snapshot;
    }
    
    /**
     * Get all compliance frameworks referenced by a field
     */
    public List<ComplianceFramework> getComplianceFrameworks(String fieldKey) {
        return profileFieldRegistryService.getFieldTypeInfo(fieldKey)
                .map(ProfileFieldTypeInfo::complianceFrameworks)
                .orElse(List.of());
    }
    
    /**
     * Get all controls for a specific framework across all fields
     */
    public Set<String> getControlsForFramework(String framework) {
        Set<String> controls = new HashSet<>();
        
        for (ProfileFieldTypeInfo fieldInfo : profileFieldRegistryService.getAllProfileFieldTypes()) {
            fieldInfo.complianceFrameworks().stream()
                    .filter(cf -> framework.equals(cf.framework()))
                    .forEach(cf -> controls.addAll(cf.controls()));
        }
        
        return controls;
    }
    
    /**
     * Get summary of all compliance frameworks used across all fields
     */
    public Map<String, Set<String>> getAllComplianceFrameworkSummary() {
        Map<String, Set<String>> summary = new HashMap<>();
        
        for (ProfileFieldTypeInfo fieldInfo : profileFieldRegistryService.getAllProfileFieldTypes()) {
            for (ComplianceFramework cf : fieldInfo.complianceFrameworks()) {
                summary.computeIfAbsent(cf.framework(), k -> new HashSet<>())
                       .addAll(cf.controls());
            }
        }
        
        return summary;
    }
}