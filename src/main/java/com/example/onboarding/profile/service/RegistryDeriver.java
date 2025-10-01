package com.example.onboarding.profile.service;

import com.example.onboarding.config.AutoProfileProperties;
import com.example.onboarding.profile.dto.FieldRegistryItem;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class RegistryDeriver {

    private final ResourceLoader resourceLoader;
    private final AutoProfileProperties props;
    private final AtomicReference<List<FieldRegistryItem>> registry = new AtomicReference<>(List.of());

    public RegistryDeriver(ResourceLoader resourceLoader, AutoProfileProperties props) {
        this.resourceLoader = resourceLoader;
        this.props = props;
    }

    @PostConstruct
    public void load() {
        try {
            Resource registryResource = resourceLoader.getResource(props.getRegistryPath());
            try (InputStream inputStream = registryResource.getInputStream()) {
                Map<String, Object> yamlContent = new Yaml().load(inputStream);
                
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> fieldDefinitions = (List<Map<String, Object>>) 
                    yamlContent.getOrDefault("fields", List.of());
                
                List<FieldRegistryItem> registryItems = fieldDefinitions.stream()
                    .map(this::parseFieldDefinition)
                    .filter(Objects::nonNull)
                    .toList();
                
                registry.set(registryItems);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load registry: " + props.getRegistryPath(), e);
        }
    }
    
    private FieldRegistryItem parseFieldDefinition(Map<String, Object> fieldMap) {
        String fieldKey = Objects.toString(fieldMap.get("key"), null);
        String derivedFrom = Objects.toString(fieldMap.get("derived_from"), null);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> ruleMap = (Map<String, Object>) fieldMap.get("rule");
        
        // Only create item if all required fields are present
        if (fieldKey != null && derivedFrom != null && ruleMap != null) {
            return new FieldRegistryItem(fieldKey, derivedFrom, ruleMap);
        }
        
        return null; // Invalid field definition, will be filtered out
    }

    /** Get registry items for UI usage */
    public List<FieldRegistryItem> getRegistryItems() {
        return registry.get();
    }

    /** Compute derived profile fields from application signal values */
    public Map<String,Object> derive(Map<String,Object> appSignals) {
        Map<String,Object> derivedFields = new LinkedHashMap<>();
        
        for (FieldRegistryItem registryItem : registry.get()) {

            // Get the application's signal value (e.g., security_rating: "A1")
            Object appSignalValue = appSignals.get(registryItem.derivedFrom());

            if (appSignalValue == null) continue;
            
            // Apply the rule mapping to derive policy requirement (e.g., A1 -> {value: "required", ttl: "0d", refresh: "per_release"})
            Map<String, Object> ruleMap = registryItem.rule();
            String signalKey = String.valueOf(appSignalValue);
            Object ruleResult = ruleMap.get(signalKey);
            if (ruleResult != null) {
                // Handle both old format (direct values) and new format (objects with value/ttl/refresh)
                if (ruleResult instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> ruleObject = (Map<String, Object>) ruleResult;
                    derivedFields.put(registryItem.key(), ruleObject);
                } else {
                    // Fallback for old format - wrap in object structure
                    Map<String, Object> wrappedValue = Map.of("value", ruleResult);
                    derivedFields.put(registryItem.key(), wrappedValue);
                }
            }
        }
        
        return derivedFields;
    }
}
