package com.example.onboarding.repository.policy;

import com.example.onboarding.dto.evidence.EvidenceDto;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Repository
public class EvidenceRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public EvidenceRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static OffsetDateTime odt(ResultSet rs, String col) throws SQLException {
        var ts = rs.getTimestamp(col);
        return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
    }

    private static final RowMapper<EvidenceDto> MAPPER = (ResultSet rs, int rn) -> new EvidenceDto(
            rs.getString("evidence_id"),
            rs.getString("profile_field_id"),
            rs.getString("uri"),
            rs.getString("type"),
            odt(rs, "added_at")
    );

    public Optional<EvidenceDto> findByProfileFieldAndSha(String profileFieldId, String sha256) {
        if (sha256 == null || sha256.isBlank()) return Optional.empty();
        var sql = """
            SELECT evidence_id, profile_field_id, uri, type, added_at
            FROM evidence
            WHERE profile_field_id=:pf AND sha256=:sha
            LIMIT 1
            """;
        var params = Map.of("pf", profileFieldId, "sha", sha256);
        return jdbc.query(sql, params, MAPPER).stream().findFirst();
    }

    public Optional<EvidenceDto> findByProfileFieldAndUri(String profileFieldId, String uri) {
        var sql = """
            SELECT evidence_id, profile_field_id, uri, type, added_at
            FROM evidence
            WHERE profile_field_id=:pf AND uri=:u
            LIMIT 1
            """;
        var params = Map.of("pf", profileFieldId, "u", uri);
        return jdbc.query(sql, params, MAPPER).stream().findFirst();
    }

    public EvidenceDto insert(String evidenceId,
                              String profileFieldId,
                              String uri,
                              String type,
                              String sha256,
                              String sourceSystem,
                              String submittedBy) {
        var sql = """
            INSERT INTO evidence (evidence_id, profile_field_id, uri, type, sha256, source_system, submitted_by)
            VALUES (:id, :pf, :uri, :type, :sha, :src, :sb)
            RETURNING evidence_id, profile_field_id, uri, type, added_at
            """;
        var params = new MapSqlParameterSource()
                .addValue("id", evidenceId)
                .addValue("pf", profileFieldId)
                .addValue("uri", uri)
                .addValue("type", type)
                .addValue("sha", sha256)
                .addValue("src", sourceSystem)
                .addValue("sb", submittedBy);
        return jdbc.queryForObject(sql, params, MAPPER);
    }

    public Optional<EvidenceDto> findById(String evidenceId) {
        var sql = """
            SELECT evidence_id, profile_field_id, uri, type, added_at
            FROM evidence
            WHERE evidence_id=:id
            """;
        return jdbc.query(sql, Map.of("id", evidenceId), MAPPER).stream().findFirst();
    }
}
