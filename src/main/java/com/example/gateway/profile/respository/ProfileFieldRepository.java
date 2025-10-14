package com.example.gateway.profile.respository;

import com.example.gateway.util.Jsons;
import com.example.gateway.util.HashIds;
import com.example.gateway.profile.service.ProfileFieldRegistryService;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Repository
public class ProfileFieldRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final ProfileFieldRegistryService profileFieldRegistryService;

    public ProfileFieldRepository(NamedParameterJdbcTemplate jdbc, 
                                 ProfileFieldRegistryService profileFieldRegistryService) {
        this.jdbc = jdbc;
        this.profileFieldRegistryService = profileFieldRegistryService;
    }

    /** Idempotent upsert (by deterministic pf_id) for all key->value entries */
    public void upsertAll(String profileId, String sourceSystem, String sourceRef, Map<String, Object> fields) {
        if (fields == null || fields.isEmpty()) return;

        String sql = """
          INSERT INTO profile_field (id, profile_id, field_key, derived_from, arb, value, source_system, source_ref, created_at, updated_at)
          VALUES (:id, :pid, :key, :derived_from, :arb, CAST(:val AS jsonb), :srcsys, :srcref, :ts, :ts)
          ON CONFLICT (id) DO UPDATE SET
            derived_from = EXCLUDED.derived_from,
            arb = EXCLUDED.arb,
            value = EXCLUDED.value,
            source_system = EXCLUDED.source_system,
            source_ref = EXCLUDED.source_ref,
            updated_at = EXCLUDED.updated_at
        """;
        List<MapSqlParameterSource> batch = new ArrayList<>(fields.size());
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        for (var e : fields.entrySet()) {
            String pfId = HashIds.profileFieldId(profileId, e.getKey());

            // Get derived_from and arb from YAML registry
            String derivedFrom = profileFieldRegistryService.getFieldTypeInfo(e.getKey())
                    .map(fieldInfo -> fieldInfo.derivedFrom())
                    .orElse(null);

            String arb = profileFieldRegistryService.getFieldTypeInfo(e.getKey())
                    .map(fieldInfo -> fieldInfo.arb())
                    .orElse(null);

            batch.add(new MapSqlParameterSource()
                    .addValue("id", pfId)
                    .addValue("pid", profileId)
                    .addValue("key", e.getKey())
                    .addValue("derived_from", derivedFrom)
                    .addValue("arb", arb)
                    .addValue("val", Jsons.toJson(e.getValue()))
                    .addValue("srcsys", sourceSystem)
                    .addValue("srcref", sourceRef)
                    .addValue("ts", now)
            );
        }
        jdbc.batchUpdate(sql, batch.toArray(MapSqlParameterSource[]::new));
    }
}
