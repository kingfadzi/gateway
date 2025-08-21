package com.example.onboarding.repository.policy;

import com.example.onboarding.dto.claims.ClaimDto;
import com.fasterxml.jackson.databind.JsonNode;
import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Repository
public class ControlClaimRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public ControlClaimRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Insert a submitted claim with a rich decision_json payload.
     * scope_type is fixed to 'application'; status is 'submitted'.
     */
    public ClaimDto insertSubmitted(String appId,
                                    String requirementId,
                                    String releaseId,
                                    String evidenceId,
                                    String method,
                                    boolean acceptable,
                                    List<String> reasons,
                                    OffsetDateTime submittedAt,
                                    JsonNode decisionJson) {

        String claimId = "clm_" + UUID.randomUUID().toString().replace("-", "");

        final String sql = """
            INSERT INTO control_claim (
                claim_id,
                requirement_id,
                scope_type,
                scope_id,
                release_id,
                method,
                status,
                submitted_at,
                decision_json
            ) VALUES (
                :claimId,
                :requirementId,
                'application',
                :appId,
                :releaseId,
                :method,
                'submitted',
                :submittedAt,
                :decisionJson
            )
            RETURNING claim_id, requirement_id, release_id, method, created_at
        """;

        var params = new MapSqlParameterSource()
                .addValue("claimId", claimId)
                .addValue("requirementId", requirementId)
                .addValue("appId", appId)
                .addValue("releaseId", releaseId)
                .addValue("method", method)
                .addValue("submittedAt", Timestamp.from(submittedAt.toInstant()))
                .addValue("decisionJson", toJsonb(decisionJson));

        return jdbc.queryForObject(sql, params, (ResultSet rs, int rowNum) ->
                new ClaimDto(
                        rs.getString("claim_id"),
                        rs.getString("requirement_id"),
                        rs.getString("release_id"),
                        evidenceId,                 // we already know it
                        acceptable,                 // we already know it
                        List.copyOf(reasons),       // we already know them
                        rs.getString("method"),
                        ts(rs, "created_at")
                )
        );
    }

    /* ---------------- helpers ---------------- */

    private static OffsetDateTime ts(ResultSet rs, String col) throws SQLException {
        var t = rs.getTimestamp(col);
        return t == null ? null : t.toInstant().atOffset(ZoneOffset.UTC);
    }

    private static PGobject toJsonb(JsonNode node) {
        try {
            var pg = new PGobject();
            pg.setType("jsonb");
            pg.setValue(node == null ? "null" : node.toString());
            return pg;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to convert decision JSON to jsonb", e);
        }
    }
}
