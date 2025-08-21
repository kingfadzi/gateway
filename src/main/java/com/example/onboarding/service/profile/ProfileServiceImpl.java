package com.example.onboarding.service.profile;

import com.example.onboarding.dto.PageResponse;
import com.example.onboarding.dto.evidence.CreateEvidenceRequest;
import com.example.onboarding.dto.evidence.EvidenceDto;
import com.example.onboarding.dto.profile.*;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

// Jackson: normalize jsonb -> native Java types
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

@Service
public class ProfileServiceImpl implements ProfileService {

    private final NamedParameterJdbcTemplate jdbc;

    public ProfileServiceImpl(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ObjectMapper to parse jsonb text into native Java values
    private final ObjectMapper om = new ObjectMapper();

    /* -------- time util: convert JDBC Timestamp -> OffsetDateTime(UTC) -------- */
    private static OffsetDateTime odt(ResultSet rs, String col) throws SQLException {
        Timestamp ts = rs.getTimestamp(col);
        return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
    }

    // Convert JSON text -> Java scalar/Map/List
    private Object jsonToJava(String json) {
        if (json == null) return null;
        try {
            JsonNode n = om.readTree(json);
            if (n.isTextual()) return n.textValue();
            if (n.isNumber())  return n.numberValue();
            if (n.isBoolean()) return n.booleanValue();
            if (n.isNull())    return null;
            return om.convertValue(n, Object.class);
        } catch (JsonProcessingException e) {
            return json; // fallback to raw string
        }
    }

    /* -------- small meta record for profile rows -------- */
    private record ProfileMeta(String profileId, int version, OffsetDateTime updatedAt) {}

    private boolean appExists(String appId) {
        Boolean exists = jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM application WHERE app_id=:id)",
                Map.of("id", appId), Boolean.class
        );
        return exists != null && exists;
    }

    private ProfileMeta getLatestProfile(String appId) {
        List<ProfileMeta> rows = jdbc.query("""
            SELECT profile_id, version, updated_at
              FROM v_app_profiles_latest
             WHERE scope_type='application' AND scope_id=:id
             ORDER BY version DESC
             LIMIT 1
        """, new MapSqlParameterSource().addValue("id", appId),
                (rs, n) -> new ProfileMeta(
                        rs.getString("profile_id"),
                        rs.getInt("version"),
                        odt(rs, "updated_at")
                ));
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Transactional
    private ProfileMeta ensureProfile(String appId, Integer version) {
        if (!appExists(appId)) throw new NoSuchElementException("Application not found: " + appId);

        if (version == null) {
            ProfileMeta latest = getLatestProfile(appId);
            if (latest != null) return latest;

            String profileId = "prof_" + UUID.randomUUID().toString().replace("-", "");
            jdbc.update("""
                INSERT INTO profile (profile_id, scope_type, scope_id, version, snapshot_at, created_at, updated_at)
                VALUES (:pid, 'application', :app, 1, now(), now(), now())
            """, new MapSqlParameterSource().addValue("pid", profileId).addValue("app", appId));
            return new ProfileMeta(profileId, 1, OffsetDateTime.now(ZoneOffset.UTC));
        } else {
            List<ProfileMeta> rows = jdbc.query("""
                SELECT profile_id, version, updated_at
                  FROM profile
                 WHERE scope_type='application' AND scope_id=:app AND version=:ver
                 LIMIT 1
            """, new MapSqlParameterSource().addValue("app", appId).addValue("ver", version),
                    (rs, n) -> new ProfileMeta(
                            rs.getString("profile_id"),
                            rs.getInt("version"),
                            odt(rs, "updated_at")
                    ));
            if (!rows.isEmpty()) return rows.get(0);

            String profileId = "prof_" + UUID.randomUUID().toString().replace("-", "");
            jdbc.update("""
                INSERT INTO profile (profile_id, scope_type, scope_id, version, snapshot_at, created_at, updated_at)
                VALUES (:pid, 'application', :app, :ver, now(), now(), now())
            """, new MapSqlParameterSource().addValue("pid", profileId).addValue("app", appId).addValue("ver", version));
            return new ProfileMeta(profileId, version, OffsetDateTime.now(ZoneOffset.UTC));
        }
    }

    /* -------- field & evidence mappers -------- */

    // value column read as text (value_json) and converted via jsonToJava(...)
    private record FieldRow(
            String fieldId, String fieldKey, String valueJson, String sourceSystem, String sourceRef,
            int evidenceCount, OffsetDateTime updatedAt) {}
    private static final RowMapper<FieldRow> FIELD_ROW_MAPPER = new RowMapper<>() {
        @Override public FieldRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new FieldRow(
                    rs.getString("field_id"),
                    rs.getString("field_key"),
                    rs.getString("value_json"),
                    rs.getString("source_system"),
                    rs.getString("source_ref"),
                    rs.getInt("evidence_count"),
                    odt(rs, "updated_at")
            );
        }
    };

    private static final RowMapper<EvidenceDto> EVIDENCE_MAPPER = new RowMapper<>() {
        @Override public EvidenceDto mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new EvidenceDto(
                    rs.getString("evidence_id"),
                    rs.getString("profile_field_id"),
                    // ensure query includes: pf.key AS profile_field_key
                    getNullableString(rs, "profile_field_key"),

                    rs.getString("uri"),
                    rs.getString("type"),
                    rs.getString("sha256"),
                    rs.getString("source_system"),
                    getNullableString(rs, "submitted_by"),                         // NEW

                    rs.getString("status"),
                    rs.getObject("valid_from",  java.time.OffsetDateTime.class),
                    rs.getObject("valid_until", java.time.OffsetDateTime.class),
                    rs.getObject("revoked_at",  java.time.OffsetDateTime.class),

                    getNullableString(rs, "reviewed_by"),                          // NEW
                    rs.getObject("reviewed_at", java.time.OffsetDateTime.class),   // NEW
                    getNullableString(rs, "tags"),                                 // NEW

                    rs.getObject("added_at",    java.time.OffsetDateTime.class),
                    rs.getObject("created_at",  java.time.OffsetDateTime.class),
                    rs.getObject("updated_at",  java.time.OffsetDateTime.class)
            );
        }
    };

    private static String getNullableString(ResultSet rs, String col) throws SQLException {
        try {
            String v = rs.getString(col);
            return (v == null || rs.wasNull()) ? null : v;
        } catch (SQLException e) {
            // Column might not be present in some queries; treat as null
            return null;
        }
    }

    /* -------- API methods -------- */

    @Override
    public ProfileSnapshotDto getProfile(String appId) {
        if (!appExists(appId)) throw new NoSuchElementException("Application not found: " + appId);

        ProfileMeta prof = getLatestProfile(appId);
        if (prof == null) {
            return new ProfileSnapshotDto(appId, null, null, List.of());
        }
        String profileId = prof.profileId();
        OffsetDateTime profUpdated = prof.updatedAt();

        // select pf.value::text AS value_json to avoid quoted scalars in API
        String fieldsSql = """
           SELECT pf.id AS field_id,
                  pf.field_key AS field_key,
                  pf.value::text AS value_json,
                  pf.source_system,
                  pf.source_ref,
                  COALESCE(ev.cnt,0) AS evidence_count,
                  pf.updated_at
             FROM profile_field pf
             LEFT JOIN (
                 SELECT profile_field_id, COUNT(*) AS cnt
                   FROM evidence
               GROUP BY profile_field_id
             ) ev ON ev.profile_field_id = pf.id
            WHERE pf.profile_id = :pid
            ORDER BY pf.field_key
        """;

        List<FieldRow> rows = jdbc.query(fieldsSql, Map.of("pid", profileId), FIELD_ROW_MAPPER);
        var fields = rows.stream()
                .map(r -> new ProfileFieldDto(
                        r.fieldId(),
                        r.fieldKey(),
                        jsonToJava(r.valueJson()),
                        r.sourceSystem(),
                        r.sourceRef(),
                        r.evidenceCount(),
                        r.updatedAt()))
                .toList();

        return new ProfileSnapshotDto(appId, profileId, profUpdated, fields);
    }

    @Override
    @Transactional
    public PatchProfileResponse patchProfile(String appId, PatchProfileRequest req) {
        ProfileMeta prof = ensureProfile(appId, req.version());
        String profileId = prof.profileId();
        Integer version = prof.version();

        List<PatchProfileResponse.UpdatedField> updated = new ArrayList<>();
        for (var f : req.fields()) {
            if (f == null || f.key() == null || f.key().isBlank())
                throw new IllegalArgumentException("Field key is required");

            String fid;
            try {
                fid = jdbc.queryForObject("""
                    SELECT id FROM profile_field
                     WHERE profile_id=:pid AND field_key=:fk
                """, new MapSqlParameterSource().addValue("pid", profileId).addValue("fk", f.key()), String.class);
            } catch (EmptyResultDataAccessException e) {
                fid = "pf_" + UUID.randomUUID().toString().replace("-", "");
            }

            jdbc.update("""
                INSERT INTO profile_field (id, profile_id, field_key, value, source_system, source_ref, collected_at, created_at, updated_at)
                VALUES (:id, :pid, :fk, CAST(:val AS jsonb), :sys, :sref, now(), now(), now())
                ON CONFLICT (profile_id, field_key)
                DO UPDATE SET value = CAST(:val AS jsonb),
                              source_system = :sys,
                              source_ref = :sref,
                              updated_at = now()
            """, new MapSqlParameterSource()
                    .addValue("id", fid)
                    .addValue("pid", profileId)
                    .addValue("fk", f.key())
                    .addValue("val", toJsonString(f.value()))
                    .addValue("sys", f.sourceSystem())
                    .addValue("sref", f.sourceRef()));

            updated.add(new PatchProfileResponse.UpdatedField(fid, f.key(), f.value()));
        }

        jdbc.update("UPDATE profile SET updated_at = now() WHERE profile_id = :pid", Map.of("pid", profileId));
        return new PatchProfileResponse(version, profileId, updated);
    }

    private String toJsonString(Object v) {
        if (v == null) return "null";
        if (v instanceof Number || v instanceof Boolean) return v.toString();
        String s = v.toString();
        if (s.startsWith("{") || s.startsWith("[") || (s.startsWith("\"") && s.endsWith("\""))) return s;
        return "\"" + s.replace("\"", "\\\"") + "\"";
    }

    @Override
    public PageResponse<EvidenceDto> listEvidence(String appId, String fieldKey, int page, int pageSize) {
        if (!appExists(appId)) throw new NoSuchElementException("Application not found: " + appId);

        ProfileMeta prof = getLatestProfile(appId);
        if (prof == null) return new PageResponse<>(page, pageSize, 0, List.of());

        String profileId = prof.profileId();
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("pid", profileId);

        String where = " WHERE pf.profile_id = :pid ";
        if (fieldKey != null && !fieldKey.isBlank()) {
            where += " AND pf.field_key = :fk ";
            p.addValue("fk", fieldKey);
        }

        long total = jdbc.queryForObject("""
            SELECT COUNT(*)
              FROM evidence e
              JOIN profile_field pf ON pf.id = e.profile_field_id
            """ + where, p, Long.class);

        int safePage = Math.max(page, 1);
        int safeSize = Math.max(pageSize, 1);
        int offset = (safePage - 1) * safeSize;
        p.addValue("limit", safeSize).addValue("offset", offset);

        List<EvidenceDto> items = jdbc.query("""
            SELECT e.*, pf.field_key AS profile_field_key
              FROM evidence e
              JOIN profile_field pf ON pf.id = e.profile_field_id
            """ + where + " ORDER BY e.created_at DESC LIMIT :limit OFFSET :offset", p, EVIDENCE_MAPPER);

        return new PageResponse<>(safePage, safeSize, total, items);
    }

    @Override
    @Transactional
    public EvidenceDto addEvidence(String appId, CreateEvidenceRequest req) {
        if (req == null) throw new IllegalArgumentException("Request body is required");

        // Resolve profile_field_id: prefer explicit id, else resolve by key under latest profile
        String profileFieldId = (req.profileFieldId() != null && !req.profileFieldId().isBlank())
                ? req.profileFieldId().trim()
                : null;

        if (profileFieldId == null) {
            String fieldKey = (req.profileField() != null && !req.profileField().isBlank())
                    ? req.profileField().trim()
                    : null;
            if (fieldKey == null) {
                throw new IllegalArgumentException("Provide either profileFieldId or profileField(fieldKey)");
            }
            ProfileMeta prof = ensureProfile(appId, null);
            String pid = prof.profileId();
            try {
                profileFieldId = jdbc.queryForObject("""
                    SELECT id FROM profile_field
                     WHERE profile_id=:pid AND field_key=:fk
                """, new MapSqlParameterSource().addValue("pid", pid).addValue("fk", fieldKey), String.class);
            } catch (EmptyResultDataAccessException e) {
                profileFieldId = "pf_" + UUID.randomUUID().toString().replace("-", "");
                jdbc.update("""
                    INSERT INTO profile_field (id, profile_id, field_key, value, created_at, updated_at)
                    VALUES (:id, :pid, :fk, '{}'::jsonb, now(), now())
                """, new MapSqlParameterSource()
                        .addValue("id", profileFieldId)
                        .addValue("pid", pid)
                        .addValue("fk", fieldKey));
            }
        }

        // Require a URI for this JSON path (file uploads handled by multipart controller elsewhere)
        if (req.uri() == null || req.uri().isBlank())
            throw new IllegalArgumentException("uri is required for JSON/link evidence");

        // Compute sha256 deterministically from URI string (for link evidence)
        String sha256 = sha256Hex(req.uri().trim().getBytes(StandardCharsets.UTF_8));

        // Insert or return existing (unique (profile_field_id, uri))
        // Also populate validity + status + source_system; keep added_at for back-compat.
        String id = "ev_" + UUID.randomUUID().toString().replace("-", "");
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("pfid", profileFieldId)
                .addValue("uri", req.uri().trim())
                .addValue("type", req.type())
                .addValue("sha", sha256)
                .addValue("src", req.sourceSystem())
                .addValue("vf", req.validFrom())
                .addValue("vu", req.validUntil());

        // Try insert with ON CONFLICT; if no row returned, fetch the existing one
        List<EvidenceDto> out = jdbc.query("""
           INSERT INTO evidence (
             evidence_id, profile_field_id, uri, type, sha256, source_system,
             valid_from, valid_until, status, added_at, created_at, updated_at
           ) VALUES (
             :id, :pfid, :uri, :type, :sha, :src,
             COALESCE(:vf, now()), :vu, 'active', now(), now(), now()
           )
           ON CONFLICT (profile_field_id, uri) DO NOTHING
           RETURNING *
        """, p, (rs, n) -> new EvidenceDto(
                rs.getString("evidence_id"),
                rs.getString("profile_field_id"),
                // ensure query includes: pf.key AS profile_field_key
                getNullableString(rs, "profile_field_key"),

                rs.getString("uri"),
                rs.getString("type"),
                rs.getString("sha256"),
                rs.getString("source_system"),
                getNullableString(rs, "submitted_by"),                         // NEW

                rs.getString("status"),
                rs.getObject("valid_from",  java.time.OffsetDateTime.class),
                rs.getObject("valid_until", java.time.OffsetDateTime.class),
                rs.getObject("revoked_at",  java.time.OffsetDateTime.class),

                getNullableString(rs, "reviewed_by"),                          // NEW
                rs.getObject("reviewed_at", java.time.OffsetDateTime.class),   // NEW
                getNullableString(rs, "tags"),                                 // NEW

                rs.getObject("added_at",    java.time.OffsetDateTime.class),
                rs.getObject("created_at",  java.time.OffsetDateTime.class),
                rs.getObject("updated_at",  java.time.OffsetDateTime.class)
        ));

        EvidenceDto dto;
        if (out.isEmpty()) {
            // existing — read with join to get field key included
            dto = jdbc.queryForObject("""
                SELECT e.*, pf.field_key AS profile_field_key
                  FROM evidence e
                  JOIN profile_field pf ON pf.id = e.profile_field_id
                 WHERE e.profile_field_id=:pfid AND e.uri=:uri
            """, new MapSqlParameterSource().addValue("pfid", profileFieldId).addValue("uri", req.uri().trim()),
                    EVIDENCE_MAPPER);
        } else {
            // inserted — need to populate field key with a join
            dto = jdbc.queryForObject("""
                SELECT e.*, pf.field_key AS profile_field_key
                  FROM evidence e
                  JOIN profile_field pf ON pf.id = e.profile_field_id
                 WHERE e.evidence_id=:id
            """, new MapSqlParameterSource().addValue("id", out.get(0).evidenceId()), EVIDENCE_MAPPER);
        }

        return dto;
    }

    @Override
    @Transactional
    public void deleteEvidence(String appId, String evidenceId) {
        if (!appExists(appId)) throw new NoSuchElementException("Application not found: " + appId);
        int n = jdbc.update("DELETE FROM evidence WHERE evidence_id=:id", Map.of("id", evidenceId));
        if (n == 0) throw new NoSuchElementException("Evidence not found");
    }

    /* -------- helpers -------- */

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(md.digest(bytes));
        } catch (Exception e) {
            throw new RuntimeException("Unable to compute sha256", e);
        }
    }
}
