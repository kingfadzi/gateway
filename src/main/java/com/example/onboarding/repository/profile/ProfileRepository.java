package com.example.onboarding.repository.profile;

import com.example.onboarding.dto.evidence.Evidence;
import com.example.onboarding.dto.profile.FieldRow;
import com.example.onboarding.dto.profile.ProfileField;
import com.example.onboarding.dto.profile.ProfileMeta;
import com.example.onboarding.util.ProfileUtils;
import com.example.onboarding.config.FieldRegistryConfig;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Repository
public class ProfileRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public ProfileRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void upsertProfile(String profileId, String scopeType, String appId, int version) {
        String sql = """
            INSERT INTO profile (profile_id, app_id, version, updated_at)
            VALUES (:pid, :app, :ver, :ts)
            ON CONFLICT (profile_id) DO UPDATE SET
              updated_at = EXCLUDED.updated_at
            """;
        jdbc.update(sql, Map.of(
                "pid", profileId,
                "app", appId,
                "ver", version,
                "ts", OffsetDateTime.now(ZoneOffset.UTC)
        ));
    }

    public boolean appExists(String appId) {
        Boolean exists = jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM application WHERE app_id=:id)",
                Map.of("id", appId), Boolean.class
        );
        return exists != null && exists;
    }

    public ProfileMeta getLatestProfileMeta(String appId) {
        List<ProfileMeta> rows = jdbc.query("""
            SELECT profile_id, version, updated_at
            FROM profile
            WHERE app_id = :id
            ORDER BY version DESC
            LIMIT 1
        """, new MapSqlParameterSource().addValue("id", appId), ProfileUtils::mapProfileMeta);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public ProfileMeta createProfile(String appId, Integer version) {
        String profileId = "prof_" + UUID.randomUUID().toString().replace("-", "");
        int actualVersion = version != null ? version : 1;
        
        jdbc.update("""
            INSERT INTO profile (profile_id, app_id, version, snapshot_at, created_at, updated_at)
            VALUES (:pid, :app, :ver, now(), now(), now())
        """, new MapSqlParameterSource()
                .addValue("pid", profileId)
                .addValue("app", appId)
                .addValue("ver", actualVersion));
        
        return new ProfileMeta(profileId, actualVersion, OffsetDateTime.now(ZoneOffset.UTC));
    }

    public ProfileMeta findProfileByVersion(String appId, int version) {
        List<ProfileMeta> rows = jdbc.query("""
            SELECT profile_id, version, updated_at
              FROM profile
             WHERE app_id=:app AND version=:ver
             LIMIT 1
        """, new MapSqlParameterSource().addValue("app", appId).addValue("ver", version), ProfileUtils::mapProfileMeta);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Map<String, Object> queryOneAsMap(String sql, Map<String, ?> params) {
        List<Map<String, Object>> rows = queryListAsMaps(sql, params);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<Map<String, Object>> queryListAsMaps(String sql, Map<String, ?> params) {
        return jdbc.query(sql, params, (rs, rowNum) -> {
            ResultSetMetaData md = rs.getMetaData();
            int cols = md.getColumnCount();
            Map<String, Object> map = new LinkedHashMap<>(cols);
            for (int i = 1; i <= cols; i++) {
                String col = md.getColumnLabel(i);
                Object val = rs.getObject(i);
                if (val instanceof Timestamp ts) {
                    val = ts.toInstant().atOffset(ZoneOffset.UTC);
                }
                map.put(col, val);
            }
            return map;
        });
    }


    public String findOrCreateProfileField(String profileId, String fieldKey) {
        try {
            return jdbc.queryForObject("""
                SELECT id FROM profile_field
                 WHERE profile_id=:pid AND field_key=:fk
            """, new MapSqlParameterSource()
                    .addValue("pid", profileId)
                    .addValue("fk", fieldKey), String.class);
        } catch (EmptyResultDataAccessException e) {
            String fieldId = "pf_" + UUID.randomUUID().toString().replace("-", "");
            jdbc.update("""
                INSERT INTO profile_field (id, profile_id, field_key, value, created_at, updated_at)
                VALUES (:id, :pid, :fk, '{}'::jsonb, now(), now())
            """, new MapSqlParameterSource()
                    .addValue("id", fieldId)
                    .addValue("pid", profileId)
                    .addValue("fk", fieldKey));
            return fieldId;
        }
    }

    public void updateProfileField(String fieldId, String profileId, String fieldKey, String valueJson, String sourceSystem, String sourceRef) {
        jdbc.update("""
            INSERT INTO profile_field (id, profile_id, field_key, value, source_system, source_ref, collected_at, created_at, updated_at)
            VALUES (:id, :pid, :fk, CAST(:val AS jsonb), :sys, :sref, now(), now(), now())
            ON CONFLICT (profile_id, field_key)
            DO UPDATE SET value = CAST(:val AS jsonb),
                          source_system = :sys,
                          source_ref = :sref,
                          updated_at = now()
        """, new MapSqlParameterSource()
                .addValue("id", fieldId)
                .addValue("pid", profileId)
                .addValue("fk", fieldKey)
                .addValue("val", valueJson)
                .addValue("sys", sourceSystem)
                .addValue("sref", sourceRef));
    }

    public void updateProfileTimestamp(String profileId) {
        jdbc.update("UPDATE profile SET updated_at = now() WHERE profile_id = :pid", Map.of("pid", profileId));
    }

    public long countEvidence(String profileId, String fieldKey) {
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("pid", profileId);
        String where = " WHERE pf.profile_id = :pid ";
        if (fieldKey != null && !fieldKey.isBlank()) {
            where += " AND pf.field_key = :fk ";
            p.addValue("fk", fieldKey);
        }
        
        return jdbc.queryForObject("""
            SELECT COUNT(*)
              FROM evidence e
              JOIN profile_field pf ON pf.id = e.profile_field_id
            """ + where, p, Long.class);
    }

    public List<Evidence> findEvidencePaginated(String profileId, String fieldKey, int limit, int offset) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("pid", profileId)
                .addValue("limit", limit)
                .addValue("offset", offset);

        String where = " WHERE pf.profile_id = :pid ";
        if (fieldKey != null && !fieldKey.isBlank()) {
            where += " AND pf.field_key = :fk ";
            p.addValue("fk", fieldKey);
        }

        return jdbc.query("""
            SELECT e.*, pf.field_key AS profile_field_key
              FROM evidence e
              JOIN profile_field pf ON pf.id = e.profile_field_id
            """ + where + " ORDER BY e.created_at DESC LIMIT :limit OFFSET :offset", p, ProfileUtils::mapEvidence);
    }

    public Evidence insertEvidence(String evidenceId, String profileFieldId, String uri, String type, String sha256, String sourceSystem, OffsetDateTime validFrom, OffsetDateTime validUntil) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("id", evidenceId)
                .addValue("pfid", profileFieldId)
                .addValue("uri", uri)
                .addValue("type", type)
                .addValue("sha", sha256)
                .addValue("src", sourceSystem)
                .addValue("vf", validFrom)
                .addValue("vu", validUntil);

        List<Evidence> result = jdbc.query("""
           INSERT INTO evidence (
             evidence_id, profile_field_id, uri, type, sha256, source_system,
             valid_from, valid_until, status, added_at, created_at, updated_at
           ) VALUES (
             :id, :pfid, :uri, :type, :sha, :src,
             COALESCE(:vf, now()), :vu, 'active', now(), now(), now()
           )
           ON CONFLICT (profile_field_id, uri) DO NOTHING
           RETURNING *
        """, p, ProfileUtils::mapEvidence);

        if (result.isEmpty()) {
            return findEvidenceByProfileFieldAndUri(profileFieldId, uri);
        } else {
            return findEvidenceById(result.get(0).evidenceId());
        }
    }

    public Evidence findEvidenceById(String evidenceId) {
        return jdbc.queryForObject("""
            SELECT e.*, pf.field_key AS profile_field_key
              FROM evidence e
              JOIN profile_field pf ON pf.id = e.profile_field_id
             WHERE e.evidence_id=:id
        """, new MapSqlParameterSource().addValue("id", evidenceId), ProfileUtils::mapEvidence);
    }

    public Evidence findEvidenceByProfileFieldAndUri(String profileFieldId, String uri) {
        return jdbc.queryForObject("""
            SELECT e.*, pf.field_key AS profile_field_key
              FROM evidence e
              JOIN profile_field pf ON pf.id = e.profile_field_id
             WHERE e.profile_field_id=:pfid AND e.uri=:uri
        """, new MapSqlParameterSource().addValue("pfid", profileFieldId).addValue("uri", uri), ProfileUtils::mapEvidence);
    }

    public int deleteEvidenceById(String evidenceId) {
        return jdbc.update("DELETE FROM evidence WHERE evidence_id=:id", Map.of("id", evidenceId));
    }

    public List<ProfileField> getProfileFields(String profileId) {
        return jdbc.query("""
            SELECT id, field_key, value, source_system, source_ref,
                   (SELECT COUNT(*) FROM evidence WHERE profile_field_id = pf.id) AS evidence_count,
                   updated_at
            FROM profile_field pf
            WHERE profile_id = :profileId
            ORDER BY field_key
        """, new MapSqlParameterSource().addValue("profileId", profileId), ProfileUtils::mapProfileField);
    }

    public List<Evidence> getEvidence(List<String> fieldIds) {
        if (fieldIds == null || fieldIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
            SELECT e.*, pf.field_key AS profile_field_key
            FROM evidence e
            JOIN profile_field pf ON pf.id = e.profile_field_id
            WHERE e.profile_field_id IN (:fieldIds)
            ORDER BY e.created_at DESC
        """, new MapSqlParameterSource().addValue("fieldIds", fieldIds), ProfileUtils::mapEvidence);
    }
    
    /**
     * Get a specific profile field by ID for TTL/status calculations
     */
    public Optional<ProfileField> getProfileFieldById(String profileFieldId) {
        try {
            ProfileField field = jdbc.queryForObject("""
                SELECT id, field_key, value, source_system, source_ref,
                       0 AS evidence_count, updated_at
                FROM profile_field
                WHERE id = :profileFieldId
            """, new MapSqlParameterSource().addValue("profileFieldId", profileFieldId), 
            ProfileUtils::mapProfileField);
            return Optional.of(field);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }


    public Map<String, Object> getApplication(String appId) {
        return queryOneAsMap(
                "SELECT * FROM application WHERE app_id = :id",
                Map.of("id", appId)
        );
    }

    public List<Map<String, Object>> getServiceInstances(String appId) {
        return queryListAsMaps(
                "SELECT * FROM service_instances WHERE app_id = :id ORDER BY created_at NULLS LAST, updated_at NULLS LAST",
                Map.of("id", appId)
        );
    }

    public List<FieldRow> getProfileFieldRows(String profileId) {
        String sql = """
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
        
        return jdbc.query(sql, Map.of("pid", profileId), ProfileUtils.FIELD_ROW_MAPPER);
    }

    public String getFieldKeyByProfileFieldId(String profileFieldId) {
        String sql = "SELECT field_key FROM profile_field WHERE id = :profileFieldId";
        try {
            return jdbc.queryForObject(sql, Map.of("profileFieldId", profileFieldId), String.class);
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("Profile field not found: " + profileFieldId);
        }
    }
}
