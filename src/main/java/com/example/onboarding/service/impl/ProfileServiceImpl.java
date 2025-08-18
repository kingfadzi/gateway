package com.example.onboarding.service.impl;

import com.example.onboarding.dto.PageResponse;
import com.example.onboarding.dto.evidence.CreateEvidenceRequest;
import com.example.onboarding.dto.evidence.EvidenceDto;
import com.example.onboarding.dto.profile.*;
import com.example.onboarding.service.ProfileService;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class ProfileServiceImpl implements ProfileService {

    private final NamedParameterJdbcTemplate jdbc;

    public ProfileServiceImpl(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /* -------- time util: convert JDBC Timestamp -> OffsetDateTime(UTC) -------- */
    private static OffsetDateTime odt(ResultSet rs, String col) throws SQLException {
        Timestamp ts = rs.getTimestamp(col);
        return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
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

    private record FieldRow(
            String fieldId, String fieldKey, Object value, String sourceSystem, String sourceRef,
            int evidenceCount, OffsetDateTime updatedAt) {}
    private static final RowMapper<FieldRow> FIELD_ROW_MAPPER = new RowMapper<>() {
        @Override public FieldRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new FieldRow(
                    rs.getString("field_id"),
                    rs.getString("field_key"),
                    rs.getObject("value"),
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
                    rs.getString("uri"),
                    rs.getString("type"),
                    odt(rs, "added_at")
            );
        }
    };

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

        String fieldsSql = """
           SELECT pf.id AS field_id,
                  pf.field_key AS field_key,
                  pf.value,
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
                        r.fieldId(), r.fieldKey(), r.value(), r.sourceSystem(), r.sourceRef(),
                        r.evidenceCount(), r.updatedAt()))
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

        int offset = (Math.max(page,1)-1) * Math.max(pageSize,1);
        p.addValue("limit", pageSize).addValue("offset", offset);

        List<EvidenceDto> items = jdbc.query("""
            SELECT e.evidence_id, e.profile_field_id, e.uri, e.type, e.added_at
              FROM evidence e
              JOIN profile_field pf ON pf.id = e.profile_field_id
            """ + where + " ORDER BY e.added_at DESC LIMIT :limit OFFSET :offset", p, EVIDENCE_MAPPER);

        return new PageResponse<>(Math.max(page,1), Math.max(pageSize,1), total, items);
    }

    @Override
    @Transactional
    public EvidenceDto addEvidence(String appId, CreateEvidenceRequest req) {
        if (req == null || req.uri() == null || req.uri().isBlank())
            throw new IllegalArgumentException("uri is required");

        String profileFieldId = req.profileFieldId();
        if ((profileFieldId == null || profileFieldId.isBlank()) && (req.fieldKey() != null && !req.fieldKey().isBlank())) {
            ProfileMeta prof = ensureProfile(appId, null);
            String pid = prof.profileId();
            try {
                profileFieldId = jdbc.queryForObject("""
                    SELECT id FROM profile_field
                     WHERE profile_id=:pid AND field_key=:fk
                """, new MapSqlParameterSource().addValue("pid", pid).addValue("fk", req.fieldKey()), String.class);
            } catch (EmptyResultDataAccessException e) {
                profileFieldId = "pf_" + UUID.randomUUID().toString().replace("-", "");
                jdbc.update("""
                    INSERT INTO profile_field (id, profile_id, field_key, value, created_at, updated_at)
                    VALUES (:id, :pid, :fk, '{}'::jsonb, now(), now())
                """, new MapSqlParameterSource()
                        .addValue("id", profileFieldId)
                        .addValue("pid", pid)
                        .addValue("fk", req.fieldKey()));
            }
        }
        if (profileFieldId == null || profileFieldId.isBlank())
            throw new IllegalArgumentException("profileFieldId or fieldKey is required");

        String id = "ev_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.update("""
           INSERT INTO evidence (evidence_id, profile_field_id, uri, type, added_at)
           VALUES (:id, :pfid, :uri, :type, now())
        """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("pfid", profileFieldId)
                .addValue("uri", req.uri())
                .addValue("type", req.type()));

        return jdbc.queryForObject("""
            SELECT evidence_id, profile_field_id, uri, type, added_at
              FROM evidence WHERE evidence_id=:id
        """, Map.of("id", id), EVIDENCE_MAPPER);
    }

    @Override
    @Transactional
    public void deleteEvidence(String appId, String evidenceId) {
        if (!appExists(appId)) throw new NoSuchElementException("Application not found: " + appId);
        int n = jdbc.update("DELETE FROM evidence WHERE evidence_id=:id", Map.of("id", evidenceId));
        if (n == 0) throw new NoSuchElementException("Evidence not found");
    }
}
