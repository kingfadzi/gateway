package com.example.onboarding.repository.track;

import com.example.onboarding.dto.track.Track;
import com.example.onboarding.dto.track.TrackSummary;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class TrackRepository {
    
    private static final Logger log = LoggerFactory.getLogger(TrackRepository.class);
    
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    
    public TrackRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Create a new track
     */
    public String createTrack(String appId, String title, String intent, String provider, 
                             String resourceType, String resourceId, String uri, 
                             Map<String, Object> attributes, OffsetDateTime openedAt) {
        String trackId = "track_" + UUID.randomUUID().toString().replace("-", "");
        
        String sql = """
            INSERT INTO track (track_id, app_id, title, intent, status, provider, resource_type, resource_id, uri, attributes, opened_at, created_at, updated_at)
            VALUES (:trackId, :appId, :title, :intent, 'open', :provider, :resourceType, :resourceId, :uri, :attributes::jsonb, :openedAt, now(), now())
            """;
        
        // Convert attributes to JSON string
        String attributesJson;
        try {
            attributesJson = objectMapper.writeValueAsString(attributes != null ? attributes : Map.of());
        } catch (Exception e) {
            log.warn("Failed to serialize attributes for track {}: {}", trackId, e.getMessage());
            attributesJson = "{}";
        }
        
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("trackId", trackId)
                .addValue("appId", appId)
                .addValue("title", title)
                .addValue("intent", intent)
                .addValue("provider", provider)
                .addValue("resourceType", resourceType)
                .addValue("resourceId", resourceId)
                .addValue("uri", uri)
                .addValue("attributes", attributesJson)
                .addValue("openedAt", openedAt));
        
        log.debug("Created track {} for app {} with resource {}:{}:{}", trackId, appId, provider, resourceType, resourceId);
        return trackId;
    }
    
    /**
     * Update an existing track
     */
    public boolean updateTrack(String trackId, String title, String status, String result, OffsetDateTime closedAt) {
        String sql = """
            UPDATE track 
            SET title = COALESCE(:title, title),
                status = COALESCE(:status, status),
                result = COALESCE(:result, result),
                closed_at = CASE WHEN :closedAt IS NOT NULL THEN :closedAt ELSE closed_at END,
                updated_at = now()
            WHERE track_id = :trackId
            """;
        
        int updated = jdbc.update(sql, new MapSqlParameterSource()
                .addValue("trackId", trackId)
                .addValue("title", title)
                .addValue("status", status)
                .addValue("result", result)
                .addValue("closedAt", closedAt));
        
        log.debug("Updated track {} (status={}, result={})", trackId, status, result);
        return updated > 0;
    }
    
    /**
     * Find track by ID
     */
    public Optional<Track> findTrackById(String trackId) {
        String sql = """
            SELECT * FROM track WHERE track_id = :trackId
            """;
        
        try {
            Track track = jdbc.queryForObject(sql, Map.of("trackId", trackId), (rs, rowNum) -> {
                // Parse attributes JSON
                Map<String, Object> attributes = parseAttributes(rs.getString("attributes"));
                
                return new Track(
                    rs.getString("track_id"),
                    rs.getString("app_id"),
                    rs.getString("title"),
                    rs.getString("intent"),
                    rs.getString("status"),
                    rs.getString("result"),
                    rs.getObject("opened_at", OffsetDateTime.class),
                    rs.getObject("closed_at", OffsetDateTime.class),
                    rs.getString("provider"),
                    rs.getString("resource_type"),
                    rs.getString("resource_id"),
                    rs.getString("uri"),
                    attributes,
                    rs.getObject("created_at", OffsetDateTime.class),
                    rs.getObject("updated_at", OffsetDateTime.class)
                );
            });
            return Optional.of(track);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
    
    /**
     * Find tracks by application ID with pagination
     */
    public List<TrackSummary> findTracksByApp(String appId, int limit, int offset) {
        String sql = """
            SELECT * FROM track 
            WHERE app_id = :appId 
            ORDER BY updated_at DESC 
            LIMIT :limit OFFSET :offset
            """;
        
        return jdbc.query(sql, Map.of("appId", appId, "limit", limit, "offset", offset), (rs, rowNum) -> {
            Map<String, Object> attributes = parseAttributes(rs.getString("attributes"));
            
            return new TrackSummary(
                rs.getString("track_id"),
                rs.getString("app_id"),
                rs.getString("title"),
                rs.getString("intent"),
                rs.getString("status"),
                rs.getString("result"),
                rs.getObject("opened_at", OffsetDateTime.class),
                rs.getObject("closed_at", OffsetDateTime.class),
                rs.getString("provider"),
                rs.getString("resource_type"),
                rs.getString("resource_id"),
                rs.getString("uri"),
                attributes,
                rs.getObject("updated_at", OffsetDateTime.class)
            );
        });
    }
    
    /**
     * Count tracks by application ID
     */
    public long countTracksByApp(String appId) {
        String sql = "SELECT COUNT(*) FROM track WHERE app_id = :appId";
        Integer count = jdbc.queryForObject(sql, Map.of("appId", appId), Integer.class);
        return count != null ? count : 0;
    }
    
    /**
     * Find track by provider, resource type, and resource ID
     */
    public Optional<Track> findTrackByResource(String provider, String resourceType, String resourceId) {
        String sql = """
            SELECT * FROM track 
            WHERE provider = :provider AND resource_type = :resourceType AND resource_id = :resourceId
            """;
        
        try {
            Track track = jdbc.queryForObject(sql, 
                Map.of("provider", provider, "resourceType", resourceType, "resourceId", resourceId), 
                (rs, rowNum) -> {
                    Map<String, Object> attributes = parseAttributes(rs.getString("attributes"));
                    
                    return new Track(
                        rs.getString("track_id"),
                        rs.getString("app_id"),
                        rs.getString("title"),
                        rs.getString("intent"),
                        rs.getString("status"),
                        rs.getString("result"),
                        rs.getObject("opened_at", OffsetDateTime.class),
                        rs.getObject("closed_at", OffsetDateTime.class),
                        rs.getString("provider"),
                        rs.getString("resource_type"),
                        rs.getString("resource_id"),
                        rs.getString("uri"),
                        attributes,
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getObject("updated_at", OffsetDateTime.class)
                    );
                }
            );
            return Optional.of(track);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
    
    /**
     * Check if application exists
     */
    public boolean applicationExists(String appId) {
        String sql = "SELECT COUNT(*) FROM application WHERE app_id = :appId";
        Integer count = jdbc.queryForObject(sql, Map.of("appId", appId), Integer.class);
        return count != null && count > 0;
    }
    
    /**
     * Parse attributes JSON string to Map
     */
    private Map<String, Object> parseAttributes(String attributesJson) {
        if (attributesJson == null || attributesJson.trim().isEmpty()) {
            return Map.of();
        }
        
        try {
            return objectMapper.readValue(attributesJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse attributes JSON: {}", e.getMessage());
            return Map.of();
        }
    }
}