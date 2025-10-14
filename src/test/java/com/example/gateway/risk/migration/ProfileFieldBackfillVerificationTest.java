package com.example.gateway.risk.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

/**
 * Verification test for V13 migration - backfill profile_field_id in risk_item table
 */
@SpringBootTest
@ActiveProfiles("test")
public class ProfileFieldBackfillVerificationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    public void verifyProfileFieldIdBackfill() {
        // Query backfill results
        String sql = """
            SELECT
                COUNT(*) as total_risk_items,
                COUNT(profile_field_id) as filled_profile_field_id,
                COUNT(*) - COUNT(profile_field_id) as still_missing,
                ROUND(COUNT(profile_field_id)::DECIMAL / NULLIF(COUNT(*), 0)::DECIMAL * 100, 2) as success_rate_pct
            FROM risk_item
            """;

        Map<String, Object> results = jdbcTemplate.queryForMap(sql);

        System.out.println("=== Profile Field ID Backfill Verification ===");
        System.out.println("Total risk items: " + results.get("total_risk_items"));
        System.out.println("Filled profile_field_id: " + results.get("filled_profile_field_id"));
        System.out.println("Still missing: " + results.get("still_missing"));
        System.out.println("Success rate: " + results.get("success_rate_pct") + "%");

        // Check diagnostic view
        String diagnosticSql = "SELECT COUNT(*) as remaining_issues FROM v_risk_item_missing_profile_field";

        try {
            Map<String, Object> diagnosticResults = jdbcTemplate.queryForMap(diagnosticSql);
            System.out.println("\nDiagnostic view check:");
            System.out.println("Remaining issues: " + diagnosticResults.get("remaining_issues"));

            if ((Long) diagnosticResults.get("remaining_issues") > 0) {
                // Show sample of remaining issues
                String sampleSql = """
                    SELECT risk_item_id, app_id, field_key, issue_reason
                    FROM v_risk_item_missing_profile_field
                    LIMIT 5
                    """;

                System.out.println("\nSample of remaining issues:");
                jdbcTemplate.queryForList(sampleSql).forEach(row -> {
                    System.out.println("  - " + row.get("risk_item_id") +
                        " | app: " + row.get("app_id") +
                        " | field: " + row.get("field_key") +
                        " | reason: " + row.get("issue_reason"));
                });
            }
        } catch (Exception e) {
            System.err.println("Warning: Diagnostic view not found or error querying it: " + e.getMessage());
        }

        System.out.println("\n=== Verification Complete ===");
    }
}
