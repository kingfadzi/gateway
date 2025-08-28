package com.example.onboarding.service.profile;

import com.example.onboarding.dto.profile.ProfileFieldTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.*;

@Service
public class ProfileFieldRegistryService {
    
    private static final Logger log = LoggerFactory.getLogger(ProfileFieldRegistryService.class);
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    
    private List<ProfileFieldTypeInfo> profileFieldTypes = new ArrayList<>();
    
    @PostConstruct
    public void loadProfileFieldRegistry() {
        try {
            ClassPathResource resource = new ClassPathResource("profile-fields.registry.yaml");
            Map<String, Object> registry = yamlMapper.readValue(resource.getInputStream(), Map.class);
            parseRegistry(registry);
            log.info("Loaded {} profile field types from registry", profileFieldTypes.size());
        } catch (IOException e) {
            log.error("Failed to load profile field registry", e);
            profileFieldTypes = List.of(); // Empty list as fallback
        }
    }
    
    @SuppressWarnings("unchecked")
    private void parseRegistry(Map<String, Object> registry) {
        profileFieldTypes = new ArrayList<>();
        
        // Extract domains (security, confidentiality, integrity, etc.)
        for (Map.Entry<String, Object> domainEntry : registry.entrySet()) {
            String domain = domainEntry.getKey();
            
            if (domainEntry.getValue() instanceof List) {
                List<Map<String, Object>> fields = (List<Map<String, Object>>) domainEntry.getValue();
                
                for (Map<String, Object> fieldMap : fields) {
                    String fieldKey = getString(fieldMap, "key");
                    String label = getString(fieldMap, "label");
                    String derivedFrom = getString(fieldMap, "derived_from");
                    
                    if (fieldKey != null && label != null) {
                        profileFieldTypes.add(new ProfileFieldTypeInfo(
                                fieldKey,
                                label,
                                domain,
                                derivedFrom
                        ));
                    }
                }
            }
        }
    }
    
    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
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
}