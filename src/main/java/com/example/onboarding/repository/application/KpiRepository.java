package com.example.onboarding.repository.application;

import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class KpiRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public KpiRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Compliant = requirements that have at least one APPROVED claim */
    public int compliant() {
        final String sql = """
            SELECT COUNT(DISTINCT pr.requirement_id) AS compliant
            FROM policy_requirement pr
            JOIN control_claim cc
              ON cc.requirement_id = pr.requirement_id
            WHERE cc.status = 'approved'
            """;
        return jdbc.queryForObject(sql, Map.of(), Integer.class);
    }

    /** Pending Review = has submitted/pending claim(s) AND no approved claim */
    public int pendingReview() {
        final String sql = """
            SELECT COUNT(*) AS pendingReview
            FROM (
              SELECT pr.requirement_id
              FROM policy_requirement pr
              LEFT JOIN control_claim cc
                ON cc.requirement_id = pr.requirement_id
              GROUP BY pr.requirement_id
              HAVING
                bool_or(cc.status IN ('submitted','pending'))
                AND NOT bool_or(cc.status = 'approved')
            ) s
            """;
        return jdbc.queryForObject(sql, Map.of(), Integer.class);
    }

    /** Missing Evidence = requirements with no claims at all */
    public int missingEvidence() {
        final String sql = """
            SELECT COUNT(*) AS missingEvidence
            FROM policy_requirement pr
            LEFT JOIN control_claim cc
              ON cc.requirement_id = pr.requirement_id
            WHERE cc.claim_id IS NULL
            """;
        return jdbc.queryForObject(sql, Map.of(), Integer.class);
    }

    /** Risk Blocked = all open risks (portfolio-wide) */
    public int riskBlocked() {
        final String sql = """
            SELECT COUNT(*) AS riskBlocked
            FROM risk_story r
            WHERE r.status = 'open'
            """;
        return jdbc.queryForObject(sql, Map.of(), Integer.class);
    }
}
