package com.example.onboarding.repository.evidence;

import com.example.onboarding.dto.evidence.Evidence;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
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
    public Evidence getById(String evidenceId) {
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
    /** Map a raw joined row (from queryForList/queryForMap) to EvidenceDto. */
    public Evidence rowToDto(Map<String, Object> row) {
        return new Evidence(
                str(row, "evidence_id"),
                str(row, "profile_field_id"),
                // ensure your SELECT adds: pf.key AS profile_field_key
                (String) row.getOrDefault("profile_field_key", null),

                str(row, "uri"),
                str(row, "type"),
                str(row, "sha256"),
                str(row, "source_system"),
                str(row, "submitted_by"),                // NEW

                str(row, "status"),
                toOffsetDateTime(row.get("valid_from")),
                toOffsetDateTime(row.get("valid_until")),
                toOffsetDateTime(row.get("revoked_at")),

                str(row, "reviewed_by"),                 // NEW
                toOffsetDateTime(row.get("reviewed_at")),// NEW
                str(row, "tags"),                        // NEW

                toOffsetDateTime(row.get("added_at")),
                toOffsetDateTime(row.get("created_at")),
                toOffsetDateTime(row.get("updated_at"))
        );
    }

    /* small null-safe string helper */
    private static String str(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v == null ? null : v.toString();
    }


    /* ===========================================================
       Helpers
       =========================================================== */

/* ===========================================================
   Helpers
   =========================================================== */

    private RowMapper<Evidence> evidenceRowMapper() {
        return new RowMapper<>() {
            @Override public Evidence mapRow(ResultSet rs, int rowNum) throws SQLException {
                return new Evidence(
                        rs.getString("evidence_id"),
                        rs.getString("profile_field_id"),
                        // ensure SELECT includes "pf.key AS profile_field_key" if you need it
                        getNullableString(rs, "profile_field_key"),

                        rs.getString("uri"),
                        rs.getString("type"),
                        rs.getString("sha256"),
                        rs.getString("source_system"),
                        rs.getString("submitted_by"),                   // NEW

                        rs.getString("status"),
                        rs.getObject("valid_from",  java.time.OffsetDateTime.class),
                        rs.getObject("valid_until", java.time.OffsetDateTime.class),
                        rs.getObject("revoked_at",  java.time.OffsetDateTime.class),

                        getNullableString(rs, "reviewed_by"),           // NEW
                        rs.getObject("reviewed_at", java.time.OffsetDateTime.class), // NEW
                        getNullableString(rs, "tags"),                  // NEW

                        rs.getObject("added_at",    java.time.OffsetDateTime.class),
                        rs.getObject("created_at",  java.time.OffsetDateTime.class),
                        rs.getObject("updated_at",  java.time.OffsetDateTime.class)
                );
            }
        };
    }

    private static String getNullableString(ResultSet rs, String col) throws SQLException {
        try {
            String v = rs.getString(col);
            return (v == null || rs.wasNull()) ? null : v;
        } catch (SQLException e) {
            // Column may be absent in some queries; treat as null
            return null;
        }
    }

    /** Accepts Timestamp, OffsetDateTime, or null */
    private static java.time.OffsetDateTime toOffsetDateTime(Object v) {
        if (v == null) return null;
        if (v instanceof java.time.OffsetDateTime odt) return odt;
        if (v instanceof java.sql.Timestamp ts) return ts.toInstant().atOffset(java.time.ZoneOffset.UTC);
        if (v instanceof java.time.Instant i)   return i.atOffset(java.time.ZoneOffset.UTC);
        // fallback for strings (e.g., JSON casting), ignore parse errors gracefully
        try {
            return java.time.OffsetDateTime.parse(v.toString());
        } catch (Exception ignore) {
            return null;
        }
    }


    /**
     * Lock an evidence row for update and return its profile_field_id + valid_from.
     * Returns Optional.empty() if no such evidence exists.
     */
    public Optional<EvidenceHead> lockHeadById(String evidenceId) {
        final String sql = """
        SELECT e.evidence_id,
               e.profile_field_id,
               COALESCE(e.valid_from, now()) AS valid_from
        FROM evidence e
        WHERE e.evidence_id = :id
        FOR UPDATE
    """;
        var params = new MapSqlParameterSource().addValue("id", evidenceId);
        return jdbc.query(sql, params, (rs, i) -> new EvidenceHead(
                rs.getString("evidence_id"),
                rs.getString("profile_field_id"),
                rs.getObject("valid_from", OffsetDateTime.class)
        )).stream().findFirst();
    }

    /** Minimal projection used for review logic. */
    public record EvidenceHead(
            String evidenceId,
            String profileFieldId,
            OffsetDateTime validFrom
    ) {}


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
