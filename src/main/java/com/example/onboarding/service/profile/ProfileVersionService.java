package com.example.onboarding.service.profile;

import com.example.onboarding.dto.profile.ProfileSnapshot;
import com.example.onboarding.dto.profile.ProfileMeta;
import com.example.onboarding.repository.profile.ProfileRepository;
import com.example.onboarding.repository.profile.ProfileFieldRepository;
import com.example.onboarding.util.HashIds;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.controlplane.auditkit.annotations.Audited;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Service
public class ProfileVersionService {
    
    private final ProfileRepository profileRepository;
    private final ProfileFieldRepository profileFieldRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(ProfileVersionService.class);
    
    public ProfileVersionService(ProfileRepository profileRepository, ProfileFieldRepository profileFieldRepository) {
        this.profileRepository = profileRepository;
        this.profileFieldRepository = profileFieldRepository;
    }
    
    /**
     * Get the current (latest) profile version for an application
     */
    public Optional<ProfileVersion> getCurrentVersion(String appId) {
        ProfileMeta meta = profileRepository.getLatestProfileMeta(appId);
        if (meta == null) {
            return Optional.empty();
        }
        
        // Get all fields for this profile version
        Map<String, Object> fields = loadProfileFields(meta.profileId());
        
        return Optional.of(new ProfileVersion(
                appId, 
                meta.profileId(), 
                meta.version(), 
                fields,
                meta.updatedAt()
        ));
    }
    
    /**
     * Create the initial profile version (version 1)
     */
    @Transactional
    @Audited(action = "CREATE_INITIAL_PROFILE", subjectType = "profile", subject = "#result.profileId", 
             context = {"appId=#appId", "version=#result.version"})
    public ProfileSnapshot createInitialProfile(String appId, String scopeType, Map<String, Object> derivedFields) {
        int version = 1;
        ProfileMeta meta = profileRepository.createProfile(appId, version);
        
        // Write all derived fields
        profileFieldRepository.upsertAll(meta.profileId(), "SERVICE_NOW", appId, derivedFields);
        
        if (log.isDebugEnabled()) {
            log.debug("Created initial profile version {} for appId={}, profileId={} with {} fields", 
                    version, appId, meta.profileId(), derivedFields.size());
        }
        
        return new ProfileSnapshot(appId, meta.profileId(), scopeType, version, derivedFields);
    }
    
    /**
     * Create a new profile version with merged fields
     */
    @Transactional
    @Audited(action = "CREATE_PROFILE_VERSION", subjectType = "profile", subject = "#result.profileId",
             context = {"appId=#appId", "version=#result.version", "previousVersion=#currentVersion.version"})
    public ProfileSnapshot createNewProfileVersion(String appId, String scopeType, 
                                                   ProfileVersion currentVersion, 
                                                   ProfileChangeAnalysis analysis,
                                                   Map<String, Object> newlyDerivedFields) {
        int newVersion = currentVersion.version() + 1;
        ProfileMeta meta = profileRepository.createProfile(appId, newVersion);
        
        // Create merged fields (unchanged + new/modified, excluding removed)
        Map<String, Object> mergedFields = analysis.createMergedFields(
                currentVersion.fields(), 
                newlyDerivedFields
        );
        
        // Write all merged fields to the new profile version
        profileFieldRepository.upsertAll(meta.profileId(), "SERVICE_NOW", appId, mergedFields);
        
        if (log.isDebugEnabled()) {
            log.debug("Created new profile version {} for appId={}, profileId={} with {} fields. Changes: {}", 
                    newVersion, appId, meta.profileId(), mergedFields.size(), analysis);
        }
        
        return new ProfileSnapshot(appId, meta.profileId(), scopeType, newVersion, mergedFields);
    }
    
    /**
     * Load all profile fields for a given profile ID
     */
    private Map<String, Object> loadProfileFields(String profileId) {
        Map<String, Object> fields = profileRepository.queryListAsMaps(
                "SELECT field_key, value FROM profile_field WHERE profile_id = :pid ORDER BY field_key",
                Map.of("pid", profileId)
        ).stream().collect(
                java.util.stream.Collectors.toMap(
                        row -> (String) row.get("field_key"),
                        row -> deserializeFieldValue(row.get("value")),
                        (existing, replacement) -> replacement,
                        java.util.LinkedHashMap::new
                )
        );
        
        if (log.isDebugEnabled()) {
            log.debug("Loaded {} profile fields for profileId={}: {}", 
                    fields.size(), profileId, fields.keySet());
        }
        
        return fields;
    }
    
    /**
     * Deserialize JSON field value back to Map object for comparison
     */
    private Object deserializeFieldValue(Object dbValue) {
        if (dbValue == null) {
            return null;
        }
        
        String jsonString = null;
        
        // Handle PostgreSQL PGobject (JSONB columns)
        if (dbValue.getClass().getSimpleName().equals("PGobject")) {
            try {
                // Use reflection to get the value since PGobject is not always on classpath
                java.lang.reflect.Method getValue = dbValue.getClass().getMethod("getValue");
                Object value = getValue.invoke(dbValue);
                jsonString = value != null ? value.toString() : null;
            } catch (Exception e) {
                log.warn("Failed to extract value from PGobject: {}", dbValue, e);
                return dbValue;
            }
        } else if (dbValue instanceof String) {
            jsonString = (String) dbValue;
        }
        
        // Deserialize JSON string to Map
        if (jsonString != null) {
            try {
                Object deserializedValue = objectMapper.readValue(jsonString, Object.class);
                if (log.isDebugEnabled()) {
                    log.debug("Deserialized DB value: {} -> {} (type: {})", 
                            jsonString, deserializedValue, 
                            deserializedValue != null ? deserializedValue.getClass().getSimpleName() : "null");
                }
                return deserializedValue;
            } catch (Exception e) {
                log.warn("Failed to deserialize JSON field value: {}", jsonString, e);
                return dbValue; // Return raw value as fallback
            }
        }
        
        if (log.isDebugEnabled()) {
            log.debug("DB value not a string or PGobject, returning as-is: {} (type: {})", 
                    dbValue, dbValue.getClass().getSimpleName());
        }
        
        return dbValue;
    }
    
    /**
     * Represents a complete profile version with all its fields
     */
    public record ProfileVersion(
            String appId,
            String profileId, 
            int version,
            Map<String, Object> fields,
            java.time.OffsetDateTime updatedAt
    ) {
        public ProfileSnapshot toSnapshot(String scopeType) {
            return new ProfileSnapshot(appId, profileId, scopeType, version, fields);
        }
    }
}