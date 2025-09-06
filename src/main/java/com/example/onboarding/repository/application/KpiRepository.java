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

    /** Compliant = count of profile fields that have approved or user attested evidence (portfolio-wide) */
    public int compliant() {
        final String sql = """
            SELECT COUNT(DISTINCT pf.id) AS compliant
            FROM profile p 
            JOIN profile_field pf ON p.profile_id = pf.profile_id
            JOIN evidence e ON e.profile_field_id = pf.id 
            JOIN evidence_field_link efl ON efl.evidence_id = e.evidence_id
            WHERE efl.link_status IN ('APPROVED', 'USER_ATTESTED')
            """;
        return jdbc.queryForObject(sql, Map.of(), Integer.class);
    }

    /** Pending Review = count of profile fields that have evidence awaiting review but no approved/attested evidence (portfolio-wide) */
    public int pendingReview() {
        final String sql = """
            SELECT COUNT(DISTINCT pf.id) AS pendingReview
            FROM profile p 
            JOIN profile_field pf ON p.profile_id = pf.profile_id
            JOIN evidence e ON e.profile_field_id = pf.id 
            JOIN evidence_field_link efl ON efl.evidence_id = e.evidence_id
            WHERE efl.link_status IN ('PENDING_PO_REVIEW', 'PENDING_SME_REVIEW')
            AND NOT EXISTS (
                SELECT 1 FROM evidence e2 
                JOIN evidence_field_link efl2 ON efl2.evidence_id = e2.evidence_id
                WHERE e2.profile_field_id = pf.id 
                AND efl2.link_status IN ('APPROVED', 'USER_ATTESTED')
            )
            """;
        return jdbc.queryForObject(sql, Map.of(), Integer.class);
    }

    /** Missing Evidence = count of profile fields that have no evidence at all (portfolio-wide) */
    public int missingEvidence() {
        final String sql = """
            SELECT COUNT(pf.id) AS missingEvidence
            FROM profile p 
            JOIN profile_field pf ON p.profile_id = pf.profile_id
            WHERE NOT EXISTS (
                SELECT 1 FROM evidence e 
                WHERE e.profile_field_id = pf.id
            )
            """;
        return jdbc.queryForObject(sql, Map.of(), Integer.class);
    }

    /** Risk Blocked = all open risks (portfolio-wide) */
    public int riskBlocked() {
        final String sql = """
            SELECT COUNT(*) FROM risk_story 
            WHERE status IN ('PENDING_SME_REVIEW', 'UNDER_REVIEW', 'OPEN')
            """;
        return jdbc.queryForObject(sql, Map.of(), Integer.class);
    }

    /** App-specific: Compliant = count of profile fields that have at least one approved or user attested evidence (latest version only) */
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
                JOIN evidence_field_link efl ON efl.evidence_id = e.evidence_id
                WHERE e.profile_field_id = pf.id 
                AND efl.link_status IN ('APPROVED', 'USER_ATTESTED')
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

    /** App-specific: Pending Review = count of profile fields that have evidence awaiting review but no approved/attested evidence (latest version only) */
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
                JOIN evidence_field_link efl ON efl.evidence_id = e.evidence_id
                WHERE e.profile_field_id = pf.id 
                AND efl.link_status IN ('PENDING_PO_REVIEW', 'PENDING_SME_REVIEW')
            )
            AND NOT EXISTS (
                SELECT 1 FROM evidence e 
                JOIN evidence_field_link efl ON efl.evidence_id = e.evidence_id
                WHERE e.profile_field_id = pf.id 
                AND efl.link_status IN ('APPROVED', 'USER_ATTESTED')
            )
            """;
        return jdbc.queryForObject(sql, Map.of("appId", appId), Integer.class);
    }

    /** App-specific: Risk Blocked = count of open risks for this specific app */
    public int riskBlockedForApp(String appId) {
        final String sql = """
            SELECT COUNT(*) FROM risk_story 
            WHERE app_id = :appId
            AND status IN ('PENDING_SME_REVIEW', 'UNDER_REVIEW', 'OPEN')
            """;
        return jdbc.queryForObject(sql, Map.of("appId", appId), Integer.class);
    }
}
