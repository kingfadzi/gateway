package com.example.onboarding.repository.application;

import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class KpiRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public KpiRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Compliant = applications that have at least one active evidence */
    public int compliant() {
        final String sql = """
            SELECT COUNT(DISTINCT a.app_id) AS compliant
            FROM application a
            WHERE EXISTS (
                SELECT 1 FROM profile p 
                JOIN profile_field pf ON p.profile_id = pf.profile_id
                JOIN evidence e ON e.profile_field_id = pf.id 
                WHERE p.app_id = a.app_id
                AND e.status = 'active'
            )
            """;
        return jdbc.queryForObject(sql, Map.of(), Integer.class);
    }

    /** Pending Review = applications that have non-active evidence but no active evidence */
    public int pendingReview() {
        final String sql = """
            SELECT COUNT(DISTINCT a.app_id) AS pendingReview
            FROM application a
            WHERE EXISTS (
                SELECT 1 FROM profile p 
                JOIN profile_field pf ON p.profile_id = pf.profile_id
                JOIN evidence e ON e.profile_field_id = pf.id 
                WHERE p.app_id = a.app_id
                AND e.status IN ('superseded','revoked')
            )
            AND NOT EXISTS (
                SELECT 1 FROM profile p 
                JOIN profile_field pf ON p.profile_id = pf.profile_id
                JOIN evidence e ON e.profile_field_id = pf.id 
                WHERE p.app_id = a.app_id
                AND e.status = 'active'
            )
            """;
        return jdbc.queryForObject(sql, Map.of(), Integer.class);
    }

    /** Missing Evidence = applications that exist but have no active evidence */
    public int missingEvidence() {
        final String sql = """
            SELECT COUNT(*) AS missingEvidence
            FROM application a
            WHERE NOT EXISTS (
                SELECT 1 FROM profile p 
                JOIN profile_field pf ON p.profile_id = pf.profile_id
                JOIN evidence e ON e.profile_field_id = pf.id 
                WHERE p.app_id = a.app_id
                AND e.status = 'active'
            )
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

    /** App-specific: Compliant = count of profile fields that have at least one active evidence (latest version only) */
    public int compliantForApp(String appId) {
        final String sql = """
            SELECT COUNT(DISTINCT pf.id) AS compliant
            FROM profile p 
            JOIN profile_field pf ON p.profile_id = pf.profile_id
            WHERE p.app_id = :appId
            AND p.version = (
                SELECT MAX(version) FROM profile 
                WHERE app_id = :appId
            )
            AND EXISTS (
                SELECT 1 FROM evidence e 
                WHERE e.profile_field_id = pf.id 
                AND e.status = 'active'
            )
            """;
        return jdbc.queryForObject(sql, Map.of("appId", appId), Integer.class);
    }

    /** App-specific: Missing Evidence = count of profile fields that have no evidence at all (latest version only) */
    public int missingEvidenceForApp(String appId) {
        final String sql = """
            SELECT COUNT(pf.id) AS missingEvidence
            FROM profile p 
            JOIN profile_field pf ON p.profile_id = pf.profile_id
            WHERE p.app_id = :appId
            AND p.version = (
                SELECT MAX(version) FROM profile 
                WHERE app_id = :appId
            )
            AND NOT EXISTS (
                SELECT 1 FROM evidence e 
                WHERE e.profile_field_id = pf.id
            )
            """;
        return jdbc.queryForObject(sql, Map.of("appId", appId), Integer.class);
    }

    /** App-specific: Pending Review = count of profile fields that have non-active evidence but no active evidence (latest version only) */
    public int pendingReviewForApp(String appId) {
        final String sql = """
            SELECT COUNT(pf.id) AS pendingReview
            FROM profile p 
            JOIN profile_field pf ON p.profile_id = pf.profile_id
            WHERE p.app_id = :appId
            AND p.version = (
                SELECT MAX(version) FROM profile 
                WHERE app_id = :appId
            )
            AND EXISTS (
                SELECT 1 FROM evidence e 
                WHERE e.profile_field_id = pf.id 
                AND e.status IN ('superseded','revoked')
            )
            AND NOT EXISTS (
                SELECT 1 FROM evidence e 
                WHERE e.profile_field_id = pf.id 
                AND e.status = 'active'
            )
            """;
        return jdbc.queryForObject(sql, Map.of("appId", appId), Integer.class);
    }

    /** App-specific: Risk Blocked = count of open risks for this specific app */
    public int riskBlockedForApp(String appId) {
        final String sql = """
            SELECT COUNT(*) AS riskBlocked
            FROM risk_story r
            WHERE r.status = 'open'
            AND r.app_id = :appId
            AND r.scope_id = :appId
            """;
        return jdbc.queryForObject(sql, Map.of("appId", appId), Integer.class);
    }
}
