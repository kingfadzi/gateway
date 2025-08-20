package com.example.onboarding.application.repository;

import com.example.onboarding.util.Jsons;
import com.example.onboarding.util.HashIds;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Repository
public class ProfileFieldRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public ProfileFieldRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Idempotent upsert (by deterministic pf_id) for all key->value entries */
    public void upsertAll(String profileId, String sourceSystem, String sourceRef, Map<String, Object> fields) {
        if (fields == null || fields.isEmpty()) return;

        String sql = """
          INSERT INTO profile_field (id, profile_id, field_key, value, source_system, source_ref, created_at, updated_at)
          VALUES (:id, :pid, :key, CAST(:val AS jsonb), :srcsys, :srcref, :ts, :ts)
          ON CONFLICT (id) DO UPDATE SET
            value = EXCLUDED.value,
            source_system = EXCLUDED.source_system,
            source_ref = EXCLUDED.source_ref,
            updated_at = EXCLUDED.updated_at
        """;
        List<MapSqlParameterSource> batch = new ArrayList<>(fields.size());
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        for (var e : fields.entrySet()) {
            String pfId = HashIds.profileFieldId(profileId, e.getKey());
            batch.add(new MapSqlParameterSource()
                    .addValue("id", pfId)
                    .addValue("pid", profileId)
                    .addValue("key", e.getKey())
                    .addValue("val", Jsons.toJson(e.getValue()))
                    .addValue("srcsys", sourceSystem)
                    .addValue("srcref", sourceRef)
                    .addValue("ts", now)
            );
        }
        jdbc.batchUpdate(sql, batch.toArray(MapSqlParameterSource[]::new));
    }
}
