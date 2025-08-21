package com.example.onboarding.repository.evidence;

import com.example.onboarding.dto.evidence.EvidenceDto;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

@Repository
public class EvidenceRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public EvidenceRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Resolve the profile_field.id for the latest application profile + field key. */
    // in com.example.onboarding.repository.evidence.EvidenceRepository

    public Optional<String> resolveProfileFieldId(String appId, String fieldKey) {
        if (fieldKey == null || fieldKey.isBlank()) return Optional.empty();

        final String k1 = fieldKey.trim();
        final String k2 = k1.contains(".") ? k1.substring(k1.lastIndexOf('.') + 1) : null;

        // Build candidates: exact key first, then last-segment fallback if dotted
        java.util.List<String> keys = new java.util.ArrayList<>();
        keys.add(k1);
        if (k2 != null && !k2.isBlank() && !k2.equals(k1)) {
            keys.add(k2);
        }

        final String sql = """
      SELECT pf.id, pf.field_key
      FROM profile p
      JOIN profile_field pf ON pf.profile_id = p.profile_id
      WHERE p.scope_type = 'application'
        AND p.scope_id   = :app
        AND p.version    = (
          SELECT MAX(version) FROM profile
           WHERE scope_type='application' AND scope_id=:app
        )
        AND pf.field_key = ANY(:keys)
      ORDER BY
        CASE pf.field_key
          WHEN :k1 THEN 0
          ELSE 1
        END
      LIMIT 1
    """;

        var params = new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                .addValue("app", appId)
                .addValue("keys", keys.toArray(new String[0]))  // Postgres ANY(array) works nicely here
                .addValue("k1", k1);

        return jdbc.query(sql, params, (rs, i) -> rs.getString("id")).stream().findFirst();
    }


    /** SHA-based dedup within the app (any field). Returns the most recent matching row if present. */
    public Optional<Map<String, Object>> findExistingInAppBySha(String appId, String sha256) {
        final String sql = """
          SELECT e.*,
                 pf.field_key AS profile_field_key
          FROM evidence e
          JOIN profile_field pf ON pf.id = e.profile_field_id
          JOIN profile p ON p.profile_id = pf.profile_id
          WHERE p.scope_type = 'application'
            AND p.scope_id   = :app
            AND p.version    = (
              SELECT MAX(version) FROM profile WHERE scope_type='application' AND scope_id=:app
            )
            AND e.sha256 = :sha
          ORDER BY e.created_at DESC
          LIMIT 1
        """;
        var params = new MapSqlParameterSource()
                .addValue("app", appId)
                .addValue("sha", sha256);
        return jdbc.queryForList(sql, params).stream().findFirst();
    }

    /**
     * Insert evidence if (profile_field_id, uri) is new; otherwise do nothing and return empty.
     * Use {@link #getByProfileFieldAndUri(String, String)} afterwards to fetch the existing one.
     */
    public Optional<Map<String, Object>> insertIfNotExists(
            String profileFieldId,
            String uri,
            String type,
            String sha256,
            String sourceSystem,
            OffsetDateTime validFrom,
            OffsetDateTime validUntil,
            OffsetDateTime now
    ) {
        final String sql = """
          INSERT INTO evidence (
            profile_field_id, uri, type, sha256, source_system,
            valid_from, valid_until, status, created_at, updated_at
          ) VALUES (
            :pf, :uri, :type, :sha, :src,
            COALESCE(:vf, now()), :vu, 'active', :now, :now
          )
          ON CONFLICT (profile_field_id, uri) DO NOTHING
          RETURNING *
        """;
        var p = new MapSqlParameterSource()
                .addValue("pf",  profileFieldId)
                .addValue("uri", uri)
                .addValue("type", type)
                .addValue("sha",  sha256)
                .addValue("src",  sourceSystem)
                .addValue("vf",   validFrom, Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("vu",   validUntil, Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("now",  now, Types.TIMESTAMP_WITH_TIMEZONE);
        return jdbc.queryForList(sql, p).stream().findFirst();
    }

    /** Fetch a single row by (profile_field_id, uri), returning a full joined map. */
    public Map<String, Object> getByProfileFieldAndUri(String profileFieldId, String uri) {
        final String sql = """
          SELECT e.*,
                 pf.field_key AS profile_field_key
          FROM evidence e
          JOIN profile_field pf ON pf.id = e.profile_field_id
          WHERE e.profile_field_id = :pf
            AND e.uri = :uri
        """;
        return jdbc.queryForMap(sql, new MapSqlParameterSource()
                .addValue("pf", profileFieldId)
                .addValue("uri", uri));
    }

    /** Read a single evidence row (joined with field key) by evidence_id. */
    public EvidenceDto getById(String evidenceId) {
        final String sql = """
          SELECT e.*,
                 pf.field_key AS profile_field_key
          FROM evidence e
          JOIN profile_field pf ON pf.id = e.profile_field_id
          WHERE e.evidence_id = :id
        """;
        try {
            return jdbc.queryForObject(
                    sql,
                    new MapSqlParameterSource().addValue("id", evidenceId),
                    evidenceRowMapper()
            );
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("Evidence not found: " + evidenceId);
        }
    }

    /** Map a raw joined row (from queryForList/queryForMap) to EvidenceDto. */
    public EvidenceDto rowToDto(Map<String, Object> row) {
        return new EvidenceDto(
                (String) row.get("evidence_id"),
                (String) row.get("profile_field_id"),
                (String) row.getOrDefault("profile_field_key", null),
                (String) row.get("uri"),
                (String) row.get("type"),
                (String) row.get("sha256"),
                (String) row.get("source_system"),
                (String) row.get("status"),
                toOffsetDateTime(row.get("valid_from")),
                toOffsetDateTime(row.get("valid_until")),
                toOffsetDateTime(row.get("revoked_at")),
                toOffsetDateTime(row.get("added_at")),
                toOffsetDateTime(row.get("created_at")),
                toOffsetDateTime(row.get("updated_at"))
        );
    }

    /* ===========================================================
       Helpers
       =========================================================== */

    private RowMapper<EvidenceDto> evidenceRowMapper() {
        return new RowMapper<>() {
            @Override public EvidenceDto mapRow(ResultSet rs, int rowNum) throws SQLException {
                return new EvidenceDto(
                        rs.getString("evidence_id"),
                        rs.getString("profile_field_id"),
                        rs.getString("profile_field_key"),
                        rs.getString("uri"),
                        rs.getString("type"),
                        rs.getString("sha256"),
                        rs.getString("source_system"),
                        rs.getString("status"),
                        rs.getObject("valid_from",  OffsetDateTime.class),
                        rs.getObject("valid_until", OffsetDateTime.class),
                        rs.getObject("revoked_at",  OffsetDateTime.class),
                        rs.getObject("added_at",    OffsetDateTime.class),
                        rs.getObject("created_at",  OffsetDateTime.class),
                        rs.getObject("updated_at",  OffsetDateTime.class)
                );
            }
        };
    }

    private static OffsetDateTime toOffsetDateTime(Object v) {
        if (v == null) return null;
        if (v instanceof OffsetDateTime odt) return odt;
        if (v instanceof java.sql.Timestamp ts) return ts.toInstant().atOffset(ZoneOffset.UTC);
        if (v instanceof java.time.Instant i)  return i.atOffset(ZoneOffset.UTC);
        // Last resort: attempt parsing
        return OffsetDateTime.parse(v.toString());
    }

    /* ===========================================================
       (Optional) Helper used by C6 create-claim path
       =========================================================== */

    /**
     * Load evidence + owning app + field metadata for claim evaluation.
     * Returns:
     *  - appId (owner), profileFieldId/key, type, uri
     *  - validity window and revokedAt
     */
    public Optional<EvidenceForClaim> findEvidenceForClaim(String evidenceId) {
        final String sql = """
          SELECT
            e.evidence_id,
            e.profile_field_id,
            pf.field_key AS profile_field_key,
            e.type,
            e.uri,
            e.valid_from,
            e.valid_until,
            e.revoked_at,
            p.scope_id   AS app_id
          FROM evidence e
          JOIN profile_field pf ON pf.id = e.profile_field_id
          JOIN profile p        ON p.profile_id = pf.profile_id
          WHERE e.evidence_id = :id
        """;
        var params = new MapSqlParameterSource().addValue("id", evidenceId);
        return jdbc.query(sql, params, (rs, i) -> new EvidenceForClaim(
                rs.getString("evidence_id"),
                rs.getString("app_id"),
                rs.getString("profile_field_id"),
                rs.getString("profile_field_key"),
                rs.getString("type"),
                rs.getString("uri"),
                rs.getObject("valid_from",  OffsetDateTime.class),
                rs.getObject("valid_until", OffsetDateTime.class),
                rs.getObject("revoked_at",  OffsetDateTime.class)
        )).stream().findFirst();
    }

    /** Minimal projection used by ControlClaimService. */
    public record EvidenceForClaim(
            String evidenceId,
            String appId,
            String profileFieldId,
            String profileFieldKey,
            String type,
            String uri,
            OffsetDateTime validFrom,
            OffsetDateTime validUntil,
            OffsetDateTime revokedAt
    ) {}
}
