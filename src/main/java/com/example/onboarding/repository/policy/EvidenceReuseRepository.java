// src/main/java/com/example/onboarding/evidence/EvidenceReuseRepository.java
package com.example.onboarding.repository.policy;

import com.example.onboarding.dto.policy.EvidenceForClaim;
import com.example.onboarding.dto.policy.ReuseCandidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Repository
public class EvidenceReuseRepository {

    private static final Logger log = LoggerFactory.getLogger(EvidenceReuseRepository.class);
    private final NamedParameterJdbcTemplate jdbc;

    public EvidenceReuseRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ReuseCandidate> findBestReusable(String appId, String profileField, Integer maxAgeDays, OffsetDateTime asOf) {
        final Timestamp asOfTs   = asOf == null ? null : Timestamp.from(asOf.toInstant());
        final boolean applyCutoff = (maxAgeDays != null && asOf != null);
        final Timestamp cutoffTs = applyCutoff ? Timestamp.from(asOf.minusDays(maxAgeDays).toInstant()) : null;

        final String sql = """
          SELECT e.evidence_id, e.valid_from, e.valid_until, e.revoked_at,
                 e.confidence, e.method, e.uri, e.sha256, e."type", e.source_system,
                 COALESCE(e.created_at, e.added_at) AS created_at
          FROM evidence e
          JOIN profile_field f ON f.id = e.profile_field_id
          JOIN profile p       ON p.profile_id = f.profile_id
          WHERE p.scope_type = 'application'
            AND p.scope_id   = :appId
            AND f.field_key  = :profileField
            AND e.revoked_at IS NULL
            AND (e.valid_from IS NULL OR e.valid_from <= :asOf)
            AND (e.valid_until IS NULL OR e.valid_until >= :asOf)
            AND (
                 :applyCutoff = false
                 OR e.valid_from IS NULL
                 OR e.valid_from >= :cutoff
            )
          ORDER BY
            CASE COALESCE(e.confidence, '')
              WHEN 'system_verified' THEN 3
              WHEN 'human_verified'  THEN 2
              WHEN 'self_attested'   THEN 1
              ELSE 0
            END DESC,
            COALESCE(e.valid_from, COALESCE(e.created_at, e.added_at)) DESC
          LIMIT 1
        """;

        Map<String, Object> params = new HashMap<>();
        params.put("appId", appId);
        params.put("profileField", profileField);
        params.put("asOf", asOfTs);
        params.put("applyCutoff", applyCutoff);
        params.put("cutoff", cutoffTs);

        try {
            var list = jdbc.query(sql, params, mapper());
            return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
        } catch (DataAccessException e) {
            var root = e.getMostSpecificCause();
            log.error("Evidence reuse SQL failed: {}", root == null ? e.getMessage() : root.getMessage());
            throw e;
        }
    }

    private static RowMapper<ReuseCandidate> mapper() {
        return new RowMapper<>() {
            @Override public ReuseCandidate mapRow(ResultSet rs, int rowNum) throws SQLException {
                return new ReuseCandidate(
                        rs.getString("evidence_id"),
                        getOdt(rs, "valid_from"),
                        getOdt(rs, "valid_until"),
                        rs.getString("confidence"),
                        rs.getString("method"),
                        rs.getString("uri"),
                        rs.getString("sha256"),
                        rs.getString("type"),
                        rs.getString("source_system"),
                        getOdt(rs, "created_at")
                );
            }
        };
    }

    private static OffsetDateTime getOdt(ResultSet rs, String col) throws SQLException {
        var ts = rs.getTimestamp(col);
        return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
    }

    /** Load evidence joined through profile_field → profile to get owning app and field meta. */
    public Optional<EvidenceForClaim> findEvidenceForClaim(String evidenceId) {
        final String sql = """
          SELECT e.evidence_id,
                 e.profile_field_id,
                 e."type",
                 e.uri,
                 e.valid_from,
                 e.valid_until,
                 e.revoked_at,
                 f.field_key,
                 p.scope_id AS app_id
          FROM evidence e
          JOIN profile_field f ON f.id = e.profile_field_id
          JOIN profile p       ON p.profile_id = f.profile_id
          WHERE e.evidence_id = :ev
            AND p.scope_type  = 'application'
          LIMIT 1
        """;
        var params = Map.of("ev", evidenceId);
        var list = jdbc.query(sql, params, (rs, rn) -> new EvidenceForClaim(
                rs.getString("evidence_id"),
                rs.getString("app_id"),
                rs.getString("profile_field_id"),
                rs.getString("field_key"),
                rs.getString("type"),
                getOdt(rs, "valid_from"),
                getOdt(rs, "valid_until"),
                getOdt(rs, "revoked_at"),
                rs.getString("uri")
        ));
        return list.stream().findFirst();
    }

    /** Quick check: does this evidence belong to the given application id? */
    public boolean evidenceBelongsToApp(String evidenceId, String appId) {
        final String sql = """
          SELECT 1
          FROM evidence e
          JOIN profile_field f ON f.id = e.profile_field_id
          JOIN profile p       ON p.profile_id = f.profile_id
          WHERE e.evidence_id = :ev
            AND p.scope_type  = 'application'
            AND p.scope_id    = :app
          LIMIT 1
        """;
        var rows = jdbc.queryForList(sql, Map.of("ev", evidenceId, "app", appId), Integer.class);
        return !rows.isEmpty();
    }

    /** Return the owning application id for this evidence (if any). */
    public Optional<String> findAppIdForEvidence(String evidenceId) {
        final String sql = """
          SELECT p.scope_id
          FROM evidence e
          JOIN profile_field f ON f.id = e.profile_field_id
          JOIN profile p       ON p.profile_id = f.profile_id
          WHERE e.evidence_id = :ev
            AND p.scope_type  = 'application'
          LIMIT 1
        """;
        var list = jdbc.queryForList(sql, Map.of("ev", evidenceId), String.class);
        return list.stream().findFirst();
    }

    /** Resolve a profile_field.id for the latest app profile by field key (underscore keys). */
    public Optional<String> resolveProfileFieldIdForAppAndKey(String appId, String fieldKey) {
        final String sql = """
          SELECT f.id
          FROM profile_field f
          JOIN profile p ON p.profile_id = f.profile_id
          WHERE p.scope_type = 'application'
            AND p.scope_id   = :app
            AND p.version = (
                SELECT MAX(version) FROM profile
                WHERE scope_type='application' AND scope_id=:app
            )
            AND f.field_key = :key
          LIMIT 1
        """;
        var list = jdbc.queryForList(sql, Map.of("app", appId, "key", fieldKey), String.class);
        return list.stream().findFirst();
    }

    /** Diagnostic helper: list available field keys for the app’s latest profile. */
    public List<String> listFieldKeysForApp(String appId) {
        final String sql = """
          SELECT f.field_key
          FROM profile_field f
          JOIN profile p ON p.profile_id = f.profile_id
          WHERE p.scope_type='application'
            AND p.scope_id=:app
            AND p.version = (
                SELECT MAX(version) FROM profile
                WHERE scope_type='application' AND scope_id=:app
            )
          ORDER BY f.field_key
        """;
        return jdbc.queryForList(sql, Map.of("app", appId), String.class);
    }
}


