package com.example.onboarding.evidence.repository;

import com.example.onboarding.evidence.dto.ReuseCandidate;
import com.example.onboarding.policy.dto.EvidenceForClaim;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * C3: Read-only selection of the best reusable evidence for a profile field.
 */
@Repository
public class EvidenceReuseRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public EvidenceReuseRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ReuseCandidate> findBestReusable(String appId,
                                                     String profileFieldKey,
                                                     Integer maxAgeDays,
                                                     OffsetDateTime asOf) {
        // Accept dotted keys (e.g., "security.encryption_at_rest") and fall back to "encryption_at_rest"
        final String k1 = (profileFieldKey == null ? "" : profileFieldKey.trim());
        final String k2 = (k1.contains(".") ? k1.substring(k1.lastIndexOf('.') + 1) : null);
        final String[] keys = (k2 != null && !k2.isBlank() && !k2.equals(k1))
                ? new String[]{k1, k2}
                : new String[]{k1};

        OffsetDateTime minFrom = (maxAgeDays == null ? null : asOf.minusDays(maxAgeDays.longValue()));

        final String sql = """
        SELECT
          e.evidence_id   AS evidence_id,
          e.valid_from    AS valid_from,
          e.valid_until   AS valid_until,
          NULL            AS confidence,   -- not in table
          NULL            AS method,       -- not in table
          e.uri           AS uri,
          e.sha256        AS sha256,
          e.type          AS type,
          e.source_system AS source_system,
          e.created_at    AS created_at
        FROM profile p
        JOIN profile_field pf ON pf.profile_id = p.profile_id
        JOIN evidence e       ON e.profile_field_id = pf.id
        WHERE p.app_id = :app
          AND p.version    = (
            SELECT MAX(version) FROM profile
            WHERE app_id=:app
          )
          AND pf.field_key = ANY(:keys)
          -- Removed status check - field deprecated
          AND (e.valid_from  IS NULL OR e.valid_from  <= :asOf::timestamptz)
          AND (e.valid_until IS NULL OR e.valid_until >= :asOf::timestamptz)
          AND (:minFrom::timestamptz IS NULL OR e.valid_from IS NULL OR e.valid_from >= :minFrom::timestamptz)
        ORDER BY
          COALESCE(e.valid_from, e.created_at) DESC
        LIMIT 1
        """;

        var params = new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                .addValue("app", appId)
                .addValue("keys", keys)  // ANY(array)
                .addValue("asOf", asOf, java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("minFrom", minFrom, java.sql.Types.TIMESTAMP_WITH_TIMEZONE);

        var rows = jdbc.query(sql, params, (rs, i) -> new ReuseCandidate(
                rs.getString("evidence_id"),
                rs.getObject("valid_from",  OffsetDateTime.class),
                rs.getObject("valid_until", OffsetDateTime.class),
                null,
                null,
                rs.getString("uri"),
                rs.getString("sha256"),
                rs.getString("type"),
                rs.getString("source_system"),
                rs.getObject("created_at", OffsetDateTime.class)
        ));
        return rows.stream().findFirst();
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
          WHERE TRUE
            AND p.scope_id   = :app
            AND p.version = (
                SELECT MAX(version) FROM profile
                WHERE app_id=:app
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
          WHERE TRUE
            AND p.scope_id=:app
            AND p.version = (
                SELECT MAX(version) FROM profile
                WHERE app_id=:app
            )
          ORDER BY f.field_key
        """;
        return jdbc.queryForList(sql, Map.of("app", appId), String.class);
    }

    public Map<String, Object> readPolicyContext(String appId) {
        // The keys OPA needs; expand if your policy grows
        List<String> keys = List.of(
                "app_criticality_assessment",
                "security_rating",
                "integrity_rating",
                "availability_rating",
                "resilience_rating",
                "has_dependencies",
                "business_service_name"
        );

        final String sql = """
        SELECT pf.field_key, pf.value
        FROM profile p
        JOIN profile_field pf ON pf.profile_id = p.profile_id
        WHERE TRUE
          AND p.scope_id   = :app
          AND p.version    = (
            SELECT MAX(version) FROM profile
            WHERE app_id = :app
          )
          AND pf.field_key IN (:keys)
    """;

        var params = new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                .addValue("app", appId)
                .addValue("keys", keys);

        Map<String, Object> out = new HashMap<>();
        jdbc.query(sql, params, rs -> {
            String k = rs.getString("field_key");
            String json = rs.getString("value");
            try {
                JsonNode n = MAPPER.readTree(json);
                Object v;
                if (n == null || n.isNull())      v = null;
                else if (n.isTextual())           v = n.asText();
                else if (n.isBoolean())           v = n.asBoolean();
                else if (n.isNumber())            v = n.numberValue();
                else                               v = MAPPER.convertValue(n, Object.class);
                out.put(k, v);
            } catch (Exception e) {
                // if parsing fails, fall back to raw string
                out.put(k, json);
            }
        });
        return out;
    }

}
