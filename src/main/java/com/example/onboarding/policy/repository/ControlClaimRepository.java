package com.example.onboarding.policy.repository;

import com.example.onboarding.claims.dto.ClaimDto;
import com.example.onboarding.claims.dto.ClaimSummary;
import com.example.onboarding.claims.dto.ClaimWithEvidence;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.postgresql.util.PGobject;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ControlClaimRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ControlClaimRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /**
     * Insert a submitted claim with a rich decision_json payload.
     * scope_type is fixed to 'application'; status is 'submitted'.
     */
    public ClaimDto insertSubmitted(String appId,
                                    String requirementId,
                                    String releaseId,
                                    String evidenceId,
                                    String method,
                                    boolean acceptable,
                                    List<String> reasons,
                                    OffsetDateTime submittedAt,
                                    JsonNode decisionJson) {

        String claimId = "clm_" + UUID.randomUUID().toString().replace("-", "");

        final String sql = """
            INSERT INTO control_claim (
                claim_id,
                requirement_id,
                scope_type,
                scope_id,
                release_id,
                method,
                status,
                submitted_at,
                decision_json
            ) VALUES (
                :claimId,
                :requirementId,
                'application',
                :appId,
                :releaseId,
                :method,
                'submitted',
                :submittedAt,
                :decisionJson
            )
            RETURNING claim_id, requirement_id, release_id, method, created_at
        """;

        var params = new MapSqlParameterSource()
                .addValue("claimId", claimId)
                .addValue("requirementId", requirementId)
                .addValue("appId", appId)
                .addValue("releaseId", releaseId)
                .addValue("method", method)
                .addValue("submittedAt", Timestamp.from(submittedAt.toInstant()))
                .addValue("decisionJson", toJsonb(decisionJson));

        return jdbc.queryForObject(sql, params, (ResultSet rs, int rowNum) ->
                new ClaimDto(
                        rs.getString("claim_id"),
                        rs.getString("requirement_id"),
                        rs.getString("release_id"),
                        evidenceId,                 // we already know it
                        acceptable,                 // we already know it
                        List.copyOf(reasons),       // we already know them
                        rs.getString("method"),
                        ts(rs, "created_at")
                )
        );
    }

    /**
     * Find or create a claim for given app, field, and track
     * Returns existing claim if found, creates new one if not
     */
    public ClaimSummary ensureClaim(String appId, String fieldKey, String trackId, String method) {
        // First try to find existing claim
        Optional<ClaimSummary> existing = findClaimByAppFieldTrack(appId, fieldKey, trackId);
        if (existing.isPresent()) {
            return existing.get();
        }
        
        // Create new claim
        String claimId = "clm_" + UUID.randomUUID().toString().replace("-", "");
        String sql = """
            INSERT INTO control_claim (
                claim_id, app_id, field_key, method, status, 
                scope_type, scope_id, track_id, created_at, updated_at
            ) VALUES (
                :claimId, :appId, :fieldKey, :method, 'open',
                'application', :appId, :trackId, now(), now()
            )
            RETURNING claim_id, app_id, field_key, method, status, track_id, created_at, updated_at
            """;
        
        return jdbc.queryForObject(sql, new MapSqlParameterSource()
                .addValue("claimId", claimId)
                .addValue("appId", appId)
                .addValue("fieldKey", fieldKey)
                .addValue("method", method != null ? method : "manual")
                .addValue("trackId", trackId),
            (rs, rowNum) -> new ClaimSummary(
                rs.getString("claim_id"),
                rs.getString("app_id"),
                rs.getString("field_key"),
                rs.getString("method"),
                rs.getString("status"),
                rs.getString("track_id"),
                ts(rs, "created_at"),
                ts(rs, "updated_at")
            )
        );
    }

    /**
     * Find existing claim by app, field, and track
     */
    public Optional<ClaimSummary> findClaimByAppFieldTrack(String appId, String fieldKey, String trackId) {
        String sql = """
            SELECT claim_id, app_id, field_key, method, status, track_id, created_at, updated_at
            FROM control_claim 
            WHERE app_id = :appId AND field_key = :fieldKey AND track_id = :trackId
            """;
        
        try {
            ClaimSummary claim = jdbc.queryForObject(sql, 
                Map.of("appId", appId, "fieldKey", fieldKey, "trackId", trackId),
                (rs, rowNum) -> new ClaimSummary(
                    rs.getString("claim_id"),
                    rs.getString("app_id"),
                    rs.getString("field_key"),
                    rs.getString("method"),
                    rs.getString("status"),
                    rs.getString("track_id"),
                    ts(rs, "created_at"),
                    ts(rs, "updated_at")
                )
            );
            return Optional.of(claim);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /**
     * Get claim with all attached evidence
     */
    public Optional<ClaimWithEvidence> findClaimWithEvidence(String claimId) {
        String sql = """
            SELECT c.claim_id, c.app_id, c.field_key, c.method, c.status,
                   c.scope_type, c.scope_id, c.track_id, c.submitted_at, 
                   c.reviewed_at, c.assigned_at, c.comment, c.decision_json,
                   c.created_at, c.updated_at
            FROM control_claim c 
            WHERE c.claim_id = :claimId
            """;
        
        try {
            ClaimWithEvidence claim = jdbc.queryForObject(sql, Map.of("claimId", claimId),
                (rs, rowNum) -> {
                    Map<String, Object> decisionJson = parseDecisionJson(rs.getString("decision_json"));
                    List<ClaimWithEvidence.EvidenceAttachment> evidence = getEvidenceForClaim(claimId);
                    
                    return new ClaimWithEvidence(
                        rs.getString("claim_id"),
                        rs.getString("app_id"),
                        rs.getString("field_key"),
                        rs.getString("method"),
                        rs.getString("status"),
                        rs.getString("scope_type"),
                        rs.getString("scope_id"),
                        rs.getString("track_id"),
                        ts(rs, "submitted_at"),
                        ts(rs, "reviewed_at"),
                        ts(rs, "assigned_at"),
                        rs.getString("comment"),
                        decisionJson,
                        evidence,
                        ts(rs, "created_at"),
                        ts(rs, "updated_at")
                    );
                }
            );
            return Optional.of(claim);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /**
     * List claims for a track with pagination
     */
    public List<ClaimSummary> findClaimsByTrack(String trackId, int limit, int offset) {
        String sql = """
            SELECT claim_id, app_id, field_key, method, status, track_id, created_at, updated_at
            FROM control_claim 
            WHERE track_id = :trackId
            ORDER BY updated_at DESC
            LIMIT :limit OFFSET :offset
            """;
        
        return jdbc.query(sql, Map.of("trackId", trackId, "limit", limit, "offset", offset),
            (rs, rowNum) -> new ClaimSummary(
                rs.getString("claim_id"),
                rs.getString("app_id"),
                rs.getString("field_key"),
                rs.getString("method"),
                rs.getString("status"),
                rs.getString("track_id"),
                ts(rs, "created_at"),
                ts(rs, "updated_at")
            )
        );
    }

    /**
     * Count claims for a track
     */
    public long countClaimsByTrack(String trackId) {
        String sql = "SELECT COUNT(*) FROM control_claim WHERE track_id = :trackId";
        Integer count = jdbc.queryForObject(sql, Map.of("trackId", trackId), Integer.class);
        return count != null ? count : 0;
    }

    /**
     * Get evidence attached to a claim
     */
    private List<ClaimWithEvidence.EvidenceAttachment> getEvidenceForClaim(String claimId) {
        String sql = """
            SELECT e.evidence_id, e.uri, e.type, e.sha256, e.source_system,
                   e.valid_from, e.valid_until, e.status, e.document_id, 
                   e.doc_version_id, e.added_at
            FROM evidence e
            WHERE e.claim_id = :claimId
            ORDER BY e.added_at DESC
            """;
        
        return jdbc.query(sql, Map.of("claimId", claimId),
            (rs, rowNum) -> new ClaimWithEvidence.EvidenceAttachment(
                rs.getString("evidence_id"),
                rs.getString("uri"),
                rs.getString("type"),
                rs.getString("sha256"),
                rs.getString("source_system"),
                ts(rs, "valid_from"),
                ts(rs, "valid_until"),
                rs.getString("status"),
                rs.getString("document_id"),
                rs.getString("doc_version_id"),
                ts(rs, "added_at")
            )
        );
    }

    /**
     * Parse decision JSON from database
     */
    private Map<String, Object> parseDecisionJson(String decisionJsonStr) {
        if (decisionJsonStr == null || decisionJsonStr.trim().isEmpty()) {
            return Map.of();
        }
        
        try {
            return objectMapper.readValue(decisionJsonStr, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    /* ---------------- helpers ---------------- */

    private static OffsetDateTime ts(ResultSet rs, String col) throws SQLException {
        var t = rs.getTimestamp(col);
        return t == null ? null : t.toInstant().atOffset(ZoneOffset.UTC);
    }

    private static PGobject toJsonb(JsonNode node) {
        try {
            var pg = new PGobject();
            pg.setType("jsonb");
            pg.setValue(node == null ? "null" : node.toString());
            return pg;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to convert decision JSON to jsonb", e);
        }
    }
}
