package com.example.gateway.portfolio.repository;

import com.example.gateway.portfolio.dto.CriticalApp;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Repository for portfolio-level risk metrics and aggregations.
 * Optimized queries for Product Owner dashboard views.
 */
@Repository
public class PortfolioRiskRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public PortfolioRiskRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Count risks requiring action (AWAITING_REMEDIATION, IN_REMEDIATION).
     */
    public int countActionRequired(String productOwner) {
        String sql = """
            SELECT COUNT(*)
            FROM risk_item ri
            JOIN application app ON ri.app_id = app.app_id
            WHERE app.product_owner = :productOwner
            AND ri.status IN ('AWAITING_REMEDIATION', 'IN_REMEDIATION')
            """;

        return jdbc.queryForObject(sql, Map.of("productOwner", productOwner), Integer.class);
    }

    /**
     * Count risks blocking compliance (CRITICAL/HIGH priority, not in terminal states).
     */
    public int countBlockingCompliance(String productOwner) {
        String sql = """
            SELECT COUNT(*)
            FROM risk_item ri
            JOIN application app ON ri.app_id = app.app_id
            WHERE app.product_owner = :productOwner
            AND ri.priority IN ('CRITICAL', 'HIGH')
            AND ri.status NOT IN ('SME_APPROVED', 'SELF_ATTESTED', 'REMEDIATED', 'CLOSED')
            """;

        return jdbc.queryForObject(sql, Map.of("productOwner", productOwner), Integer.class);
    }

    /**
     * Count risks with missing evidence (not in terminal states).
     */
    public int countMissingEvidence(String productOwner) {
        String sql = """
            SELECT COUNT(*)
            FROM risk_item ri
            JOIN application app ON ri.app_id = app.app_id
            WHERE app.product_owner = :productOwner
            AND ri.evidence_status = 'missing'
            AND ri.status NOT IN ('SME_APPROVED', 'SELF_ATTESTED', 'REMEDIATED', 'CLOSED')
            """;

        return jdbc.queryForObject(sql, Map.of("productOwner", productOwner), Integer.class);
    }

    /**
     * Count risks pending review (PENDING_REVIEW, UNDER_SME_REVIEW, PENDING_APPROVAL).
     */
    public int countPendingReview(String productOwner) {
        String sql = """
            SELECT COUNT(*)
            FROM risk_item ri
            JOIN application app ON ri.app_id = app.app_id
            WHERE app.product_owner = :productOwner
            AND ri.status IN ('PENDING_REVIEW', 'UNDER_SME_REVIEW', 'PENDING_APPROVAL')
            """;

        return jdbc.queryForObject(sql, Map.of("productOwner", productOwner), Integer.class);
    }

    /**
     * Count escalated risks.
     */
    public int countEscalated(String productOwner) {
        String sql = """
            SELECT COUNT(*)
            FROM risk_item ri
            JOIN application app ON ri.app_id = app.app_id
            WHERE app.product_owner = :productOwner
            AND ri.status = 'ESCALATED'
            """;

        return jdbc.queryForObject(sql, Map.of("productOwner", productOwner), Integer.class);
    }

    /**
     * Count recent wins (resolved in last 7 days).
     */
    public int countRecentWins(String productOwner) {
        OffsetDateTime sevenDaysAgo = OffsetDateTime.now().minusDays(7);

        String sql = """
            SELECT COUNT(*)
            FROM risk_item ri
            JOIN application app ON ri.app_id = app.app_id
            WHERE app.product_owner = :productOwner
            AND ri.status IN ('SME_APPROVED', 'REMEDIATED', 'CLOSED')
            AND ri.resolved_at >= :sevenDaysAgo
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("productOwner", productOwner)
            .addValue("sevenDaysAgo", sevenDaysAgo);

        return jdbc.queryForObject(sql, params, Integer.class);
    }

    /**
     * Get top 10 critical apps with active high-severity risks.
     */
    public List<CriticalApp> getCriticalApps(String productOwner) {
        String sql = """
            SELECT
                app.app_id,
                app.name AS app_name,
                COUNT(CASE WHEN ri.severity = 'critical' THEN 1 END) AS critical_count,
                COUNT(CASE WHEN ri.severity = 'high' THEN 1 END) AS high_count,
                MAX(ri.priority_score) AS risk_score
            FROM application app
            JOIN risk_item ri ON app.app_id = ri.app_id
            WHERE app.product_owner = :productOwner
            AND ri.status NOT IN ('SME_APPROVED', 'SELF_ATTESTED', 'REMEDIATED', 'CLOSED')
            GROUP BY app.app_id, app.name
            HAVING COUNT(CASE WHEN ri.severity = 'critical' THEN 1 END) > 0
            ORDER BY critical_count DESC, risk_score DESC
            LIMIT 10
            """;

        return jdbc.query(sql, Map.of("productOwner", productOwner), (rs, rowNum) ->
            new CriticalApp(
                rs.getString("app_id"),
                rs.getString("app_name"),
                rs.getInt("critical_count"),
                rs.getInt("high_count"),
                rs.getInt("risk_score")
            )
        );
    }

    /**
     * Count total applications owned by product owner.
     */
    public int countTotalApps(String productOwner) {
        String sql = """
            SELECT COUNT(*)
            FROM application
            WHERE product_owner = :productOwner
            """;

        return jdbc.queryForObject(sql, Map.of("productOwner", productOwner), Integer.class);
    }

    /**
     * Count applications with at least 1 active risk.
     */
    public int countAppsWithRisks(String productOwner) {
        String sql = """
            SELECT COUNT(DISTINCT app.app_id)
            FROM application app
            JOIN risk_item ri ON app.app_id = ri.app_id
            WHERE app.product_owner = :productOwner
            AND ri.status NOT IN ('SME_APPROVED', 'SELF_ATTESTED', 'REMEDIATED', 'CLOSED')
            """;

        return jdbc.queryForObject(sql, Map.of("productOwner", productOwner), Integer.class);
    }
}
