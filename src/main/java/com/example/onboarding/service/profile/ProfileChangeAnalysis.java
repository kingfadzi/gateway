package com.example.onboarding.service.profile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

/**
 * Simple change analysis - if anything changed, added, or removed, create new version
 */
public class ProfileChangeAnalysis {
    
    private static final Logger log = LoggerFactory.getLogger(ProfileChangeAnalysis.class);
    
    private final Set<String> addedFields;
    private final Set<String> removedFields;
    private final Set<String> modifiedFields;
    private final boolean hasChanges;
    
    private ProfileChangeAnalysis(Set<String> addedFields, Set<String> removedFields, Set<String> modifiedFields) {
        this.addedFields = Collections.unmodifiableSet(addedFields);
        this.removedFields = Collections.unmodifiableSet(removedFields);
        this.modifiedFields = Collections.unmodifiableSet(modifiedFields);
        this.hasChanges = !addedFields.isEmpty() || !removedFields.isEmpty() || !modifiedFields.isEmpty();
    }
    
    public boolean requiresNewVersion() {
        return hasChanges;
    }
    
    public Set<String> getAddedFields() {
        return addedFields;
    }
    
    public Set<String> getRemovedFields() {
        return removedFields;
    }
    
    public Set<String> getModifiedFields() {
        return modifiedFields;
    }
    
    /**
     * Creates the merged field set for a new profile version.
     * Takes all unchanged fields from current + all new/modified fields from derivation.
     * Excludes removed fields.
     */
    public Map<String, Object> createMergedFields(Map<String, Object> currentFields, 
                                                  Map<String, Object> newFields) {
        Map<String, Object> merged = new LinkedHashMap<>();
        
        // Add all fields that haven't been removed
        currentFields.entrySet().stream()
                .filter(entry -> !removedFields.contains(entry.getKey()))
                .forEach(entry -> merged.put(entry.getKey(), entry.getValue()));
        
        // Overwrite with new/modified fields from derivation
        newFields.forEach((key, value) -> {
            if (addedFields.contains(key) || modifiedFields.contains(key)) {
                merged.put(key, value);
            }
        });
        
        return merged;
    }
    
    /**
     * Factory method to analyze changes between current and newly derived fields
     */
    public static ProfileChangeAnalysis analyze(Map<String, Object> currentFields, 
                                                Map<String, Object> newFields) {
        Set<String> addedFields = new HashSet<>();
        Set<String> removedFields = new HashSet<>();
        Set<String> modifiedFields = new HashSet<>();
        
        if (log.isDebugEnabled()) {
            log.debug("Analyzing changes. Current fields: {}, New fields: {}", 
                    currentFields.keySet(), newFields.keySet());
        }
        
        // Find added fields
        newFields.keySet().stream()
                .filter(key -> !currentFields.containsKey(key))
                .forEach(key -> {
                    addedFields.add(key);
                    if (log.isDebugEnabled()) {
                        log.debug("Added field: {} = {}", key, newFields.get(key));
                    }
                });
        
        // Find removed fields
        currentFields.keySet().stream()
                .filter(key -> !newFields.containsKey(key))
                .forEach(key -> {
                    removedFields.add(key);
                    if (log.isDebugEnabled()) {
                        log.debug("Removed field: {} (was: {})", key, currentFields.get(key));
                    }
                });
        
        // Find modified fields (exist in both but different values)
        currentFields.entrySet().stream()
                .filter(entry -> newFields.containsKey(entry.getKey()))
                .filter(entry -> {
                    Object currentValue = entry.getValue();
                    Object newValue = newFields.get(entry.getKey());
                    boolean different = !Objects.equals(currentValue, newValue);
                    
                    if (different && log.isDebugEnabled()) {
                        log.debug("Modified field: {} changed from {} to {}", 
                                entry.getKey(), currentValue, newValue);
                        log.debug("Current value type: {}, New value type: {}", 
                                currentValue != null ? currentValue.getClass().getSimpleName() : "null",
                                newValue != null ? newValue.getClass().getSimpleName() : "null");
                    }
                    
                    return different;
                })
                .map(Map.Entry::getKey)
                .forEach(modifiedFields::add);
        
        ProfileChangeAnalysis analysis = new ProfileChangeAnalysis(addedFields, removedFields, modifiedFields);
        
        if (log.isDebugEnabled()) {
            log.debug("Change analysis result: {}", analysis);
        }
        
        return analysis;
    }
    
    @Override
    public String toString() {
        if (!hasChanges) {
            return "ProfileChangeAnalysis{no changes}";
        }
        
        List<String> changes = new ArrayList<>();
        if (!addedFields.isEmpty()) changes.add("added: " + addedFields);
        if (!removedFields.isEmpty()) changes.add("removed: " + removedFields);
        if (!modifiedFields.isEmpty()) changes.add("modified: " + modifiedFields);
        
        return "ProfileChangeAnalysis{" + String.join(", ", changes) + "}";
    }
}