package com.example.onboarding.repository.policy;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;

@Repository
public class ProfileFieldLookupRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public ProfileFieldLookupRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Replace resolveProfileFieldIdForAppAndKey(...) with this version:
    public Optional<String> resolveProfileFieldIdForAppAndKey(String appId, String fieldKey) {
        // Use the view to always target the latest profile for the app
        var sql = """
        SELECT f.id
        FROM profile_field f
        JOIN v_app_profiles_latest p ON p.profile_id = f.profile_id
        WHERE p.scope_type = 'application'
          AND p.scope_id   = :app
          AND f.field_key  = :key
        LIMIT 1
        """;
        var list = jdbc.queryForList(sql, Map.of("app", appId, "key", fieldKey), String.class);
        return list.stream().findFirst();
    }

    /** Optionally list available field keys for diagnostics */
    public java.util.List<String> listFieldKeysForApp(String appId) {
        var sql = """
        SELECT f.field_key
        FROM profile_field f
        JOIN v_app_profiles_latest p ON p.profile_id = f.profile_id
        WHERE p.scope_type='application' AND p.scope_id=:app
        ORDER BY f.field_key
        """;
        return jdbc.queryForList(sql, Map.of("app", appId), String.class);
    }

}
