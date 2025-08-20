package com.example.onboarding.repository.application;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@Repository
public class ProfileRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public ProfileRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void upsertProfile(String profileId, String scopeType, String appId, int version) {
        String sql = """
            INSERT INTO profile (profile_id, scope_type, scope_id, version, updated_at)
            VALUES (:pid, :stype, :sid, :ver, :ts)
            ON CONFLICT (profile_id) DO UPDATE SET
              updated_at = EXCLUDED.updated_at
            """;
        jdbc.update(sql, Map.of(
                "pid", profileId,
                "stype", scopeType,
                "sid", appId,
                "ver", version,
                "ts", OffsetDateTime.now(ZoneOffset.UTC)
        ));
    }
}
