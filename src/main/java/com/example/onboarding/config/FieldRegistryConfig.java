package com.example.onboarding.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class FieldRegistryConfig {

    private ProfileFieldRegistry registry;
    private Map<String, String> fieldToDerivedFromMap;

    @PostConstruct
    public void loadRegistry() throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        ClassPathResource resource = new ClassPathResource("profile-fields.registry.yaml");
        
        this.registry = mapper.readValue(resource.getInputStream(), ProfileFieldRegistry.class);
        
        // Build lookup map for quick access
        this.fieldToDerivedFromMap = registry.fields.stream()
                .collect(Collectors.toMap(
                        field -> field.key,
                        field -> field.derivedFrom
                ));
    }

    public String getDerivedFromByFieldKey(String fieldKey) {
        return fieldToDerivedFromMap.getOrDefault(fieldKey, "unknown");
    }

    public ProfileFieldRegistry getRegistry() {
        return registry;
    }

    // Inner classes for YAML mapping
    public static class ProfileFieldRegistry {
        @JsonProperty("version")
        public int version;
        
        @JsonProperty("updated_at")
        public String updatedAt;
        
        @JsonProperty("fields")
        public List<FieldDefinition> fields;
    }

    public static class FieldDefinition {
        @JsonProperty("key")
        public String key;
        
        @JsonProperty("label")
        public String label;
        
        @JsonProperty("derived_from")
        public String derivedFrom;
        
        @JsonProperty("rule")
        public Map<String, Object> rule;
    }
}