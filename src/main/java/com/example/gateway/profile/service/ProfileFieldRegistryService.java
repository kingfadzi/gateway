package com.example.gateway.profile.service;

import com.example.gateway.profile.dto.ProfileFieldTypeInfo;
import com.example.gateway.registry.dto.FieldRiskConfig;
import com.example.gateway.registry.dto.RiskCreationRule;
import com.example.gateway.registry.dto.ComplianceFramework;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ProfileFieldRegistryService {
    
    private static final Logger log = LoggerFactory.getLogger(ProfileFieldRegistryService.class);
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    
    private List<ProfileFieldTypeInfo> profileFieldTypes = new ArrayList<>();
    private Map<String, FieldRiskConfig> fieldRiskConfigs = new HashMap<>();
    private Map<String, Object> rawRegistry;
    
    @PostConstruct
    public void loadProfileFieldRegistry() {
        try {
            ClassPathResource resource = new ClassPathResource("profile-fields.registry.yaml");
            rawRegistry = yamlMapper.readValue(resource.getInputStream(), Map.class);
            parseRegistry(rawRegistry);
            log.info("Loaded {} profile field types and {} risk configurations from registry", 
                    profileFieldTypes.size(), fieldRiskConfigs.size());
        } catch (IOException e) {
            log.error("Failed to load profile field registry", e);
            profileFieldTypes = List.of(); // Empty list as fallback
        }
    }
    
    @SuppressWarnings("unchecked")
    private void parseRegistry(Map<String, Object> registry) {
        profileFieldTypes = new ArrayList<>();
        fieldRiskConfigs = new HashMap<>();

        // Extract the "fields" array from the registry
        Object fieldsObj = registry.get("fields");
        if (fieldsObj instanceof List) {
            List<Map<String, Object>> fields = (List<Map<String, Object>>) fieldsObj;
            
            for (Map<String, Object> fieldMap : fields) {
                String fieldKey = getString(fieldMap, "key");
                String label = getString(fieldMap, "label");
                String derivedFrom = getString(fieldMap, "derived_from");
                String arb = getString(fieldMap, "arb");

                if (fieldKey != null && label != null) {
                    // Parse compliance frameworks
                    List<ComplianceFramework> complianceFrameworks = parseComplianceFrameworks(fieldMap);

                    // Calculate domain from derived_from field
                    String domain = getDomainFromDerivedFrom(derivedFrom);

                    // Create ProfileFieldTypeInfo (existing functionality)
                    profileFieldTypes.add(new ProfileFieldTypeInfo(
                            fieldKey,
                            label,
                            domain,
                            derivedFrom,
                            arb,
                            complianceFrameworks
                    ));
                    
                    // Parse risk configuration from rules
                    Object rulesObj = fieldMap.get("rule");
                    if (rulesObj instanceof Map) {
                        Map<String, Object> rulesMap = (Map<String, Object>) rulesObj;
                        Map<String, RiskCreationRule> riskRules = parseRiskRules(rulesMap);
                        
                        if (!riskRules.isEmpty()) {
                            fieldRiskConfigs.put(fieldKey, new FieldRiskConfig(
                                    fieldKey, label, derivedFrom, riskRules
                            ));
                        }
                    }
                }
            }
        }
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, RiskCreationRule> parseRiskRules(Map<String, Object> rulesMap) {
        Map<String, RiskCreationRule> riskRules = new HashMap<>();
        
        for (Map.Entry<String, Object> ruleEntry : rulesMap.entrySet()) {
            String criticality = ruleEntry.getKey();
            Object ruleValue = ruleEntry.getValue();
            
            if (ruleValue instanceof Map) {
                Map<String, Object> rule = (Map<String, Object>) ruleValue;
                
                String value = getString(rule, "value");
                String label = getString(rule, "label");
                String ttl = getString(rule, "ttl");
                Boolean requiresReview = getBoolean(rule, "requires_review");
                String priority = getString(rule, "priority");

                if (value != null && label != null) {
                    riskRules.put(criticality, RiskCreationRule.fromRegistryRule(
                            value, label, ttl, requiresReview, priority
                    ));
                }
            }
        }
        
        return riskRules;
    }
    
    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }
    
    private Boolean getBoolean(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return null;
    }
    
    @SuppressWarnings("unchecked")
    private List<ComplianceFramework> parseComplianceFrameworks(Map<String, Object> fieldMap) {
        List<ComplianceFramework> frameworks = new ArrayList<>();
        
        Object complianceObj = fieldMap.get("compliance_frameworks");
        if (complianceObj instanceof List) {
            List<Map<String, Object>> complianceList = (List<Map<String, Object>>) complianceObj;
            
            for (Map<String, Object> frameworkMap : complianceList) {
                String framework = getString(frameworkMap, "framework");
                Object controlsObj = frameworkMap.get("controls");
                
                if (framework != null && controlsObj instanceof List) {
                    List<String> controls = new ArrayList<>();
                    List<?> controlsList = (List<?>) controlsObj;
                    
                    for (Object control : controlsList) {
                        if (control != null) {
                            controls.add(control.toString());
                        }
                    }
                    
                    frameworks.add(new ComplianceFramework(framework, controls));
                }
            }
        }
        
        return frameworks;
    }
    
    /**
     * Calculate domain from derived_from field by removing _rating suffix
     */
    private String getDomainFromDerivedFrom(String derivedFrom) {
        if (derivedFrom == null || derivedFrom.isEmpty()) {
            return "unknown";
        }
        
        // Remove _rating suffix to get domain name
        if (derivedFrom.endsWith("_rating")) {
            return derivedFrom.substring(0, derivedFrom.length() - 7); // Remove "_rating"
        }
        
        // Handle special cases like "artifact" 
        return derivedFrom;
    }
    
    /**
     * Get all available profile field types for frontend dropdowns
     */
    public List<ProfileFieldTypeInfo> getAllProfileFieldTypes() {
        return List.copyOf(profileFieldTypes);
    }
    
    /**
     * Get profile field types grouped by domain
     */
    public Map<String, List<ProfileFieldTypeInfo>> getProfileFieldTypesByDomain() {
        Map<String, List<ProfileFieldTypeInfo>> byDomain = new LinkedHashMap<>();
        
        for (ProfileFieldTypeInfo fieldType : profileFieldTypes) {
            byDomain.computeIfAbsent(fieldType.domain(), k -> new ArrayList<>()).add(fieldType);
        }
        
        return byDomain;
    }
    
    /**
     * Get field type info by field key
     */
    public Optional<ProfileFieldTypeInfo> getFieldTypeInfo(String fieldKey) {
        return profileFieldTypes.stream()
                .filter(ft -> ft.fieldKey().equals(fieldKey))
                .findFirst();
    }
    
    /**
     * Validate that field keys exist in the registry
     */
    public List<String> validateFieldKeys(List<String> fieldKeys) {
        Set<String> validKeys = new HashSet<>();
        for (ProfileFieldTypeInfo fieldType : profileFieldTypes) {
            validKeys.add(fieldType.fieldKey());
        }
        
        return fieldKeys.stream()
                .filter(validKeys::contains)
                .toList();
    }
    
    // =====================
    // Risk Configuration Methods
    // =====================
    
    /**
     * Get risk configuration for a field
     */
    public Optional<FieldRiskConfig> getFieldRiskConfig(String fieldKey) {
        return Optional.ofNullable(fieldRiskConfigs.get(fieldKey));
    }
    
    /**
     * Check if field requires review for given app criticality
     */
    public boolean requiresReviewForField(String fieldKey, String appCriticality) {
        return getFieldRiskConfig(fieldKey)
                .map(config -> config.requiresReviewForCriticality(appCriticality))
                .orElse(false);
    }
    
    /**
     * Get all fields that have risk configurations
     */
    public List<String> getFieldsWithRiskConfig() {
        return new ArrayList<>(fieldRiskConfigs.keySet());
    }
    
    /**
     * Get all risk configurations
     */
    public Map<String, FieldRiskConfig> getAllRiskConfigs() {
        return Map.copyOf(fieldRiskConfigs);
    }
    
    /**
     * Get risk creation rule for specific field and criticality
     */
    public Optional<RiskCreationRule> getRiskRule(String fieldKey, String appCriticality) {
        return getFieldRiskConfig(fieldKey)
                .map(config -> config.getRuleForCriticality(appCriticality));
    }

    public Map<String, Object> getRawRegistry() {
        return rawRegistry;
    }

    public List<String> getDomains() {
        return profileFieldTypes.stream()
                .map(ProfileFieldTypeInfo::derivedFrom)
                .distinct()
                .collect(Collectors.toList());
    }

    public List<String> getControlsByDomain(String domain) {
        return profileFieldTypes.stream()
                .filter(fieldType -> domain.equals(fieldType.derivedFrom()))
                .map(ProfileFieldTypeInfo::fieldKey)
                .collect(Collectors.toList());
    }

    // =====================
    // ARB Routing Methods
    // =====================

    /**
     * Get ARB name for a given derived_from field (e.g., "security_rating" -> "security")
     * Finds the first field with this derived_from and returns its arb
     */
    public Optional<String> getArbForDerivedFrom(String derivedFrom) {
        return profileFieldTypes.stream()
                .filter(ft -> derivedFrom.equals(ft.derivedFrom()))
                .map(ProfileFieldTypeInfo::arb)
                .findFirst();
    }

    /**
     * Get all ARB routing configurations (derived_from -> arb mapping)
     * Builds map from unique derived_from values across all fields
     */
    public Map<String, String> getAllArbRouting() {
        Map<String, String> routing = new HashMap<>();
        for (ProfileFieldTypeInfo fieldType : profileFieldTypes) {
            if (fieldType.derivedFrom() != null && fieldType.arb() != null) {
                routing.putIfAbsent(fieldType.derivedFrom(), fieldType.arb());
            }
        }
        return Map.copyOf(routing);
    }

    /**
     * Get ARB name for a field directly from its arb property
     */
    public Optional<String> getArbForField(String fieldKey) {
        return getFieldTypeInfo(fieldKey)
                .map(ProfileFieldTypeInfo::arb);
    }

    /**
     * Get derived_from value for a field
     */
    public Optional<String> getDerivedFromForField(String fieldKey) {
        return getFieldTypeInfo(fieldKey)
                .map(ProfileFieldTypeInfo::derivedFrom);
    }
}