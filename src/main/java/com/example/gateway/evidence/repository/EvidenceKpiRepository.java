package com.example.gateway.evidence.repository;

import com.example.gateway.evidence.dto.KpiEvidenceSummary;
import com.example.gateway.evidence.dto.RiskBlockedItem;
import com.example.gateway.evidence.model.EvidenceFieldLinkStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Repository for KPI-based evidence queries.
 * Handles queries for compliance states: compliant, pending review, missing evidence, and risk-blocked.
 */
@Repository
public class EvidenceKpiRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public EvidenceKpiRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Find evidence by KPI state: COMPLIANT
     * Returns evidence that has approved or user attested link status
     */
    public List<KpiEvidenceSummary> findCompliantEvidence(
            String appId, String criticality, String domain, String fieldKey, String search, int limit, int offset) {
        StringBuilder sqlBuilder = new StringBuilder("""
            SELECT DISTINCT e.evidence_id, e.app_id, e.profile_field_id, e.claim_id, e.uri, e.type, e.status,
                   e.submitted_by, e.valid_from, e.valid_until, e.track_id, e.document_id, e.doc_version_id,
                   e.created_at, e.updated_at,
                   efl.link_status, efl.linked_by, efl.linked_at,
                   efl.reviewed_by, efl.reviewed_at, efl.review_comment,
                   d.title as document_title, d.source_type as document_source_type,
                   d.owners as document_owners, d.link_health as document_link_health,
                   pf.field_key, pf.derived_from,
                   app.name as app_name, app.product_owner, app.application_tier,
                   app.architecture_type, app.install_type, app.app_criticality_assessment,
                   app.security_rating, app.confidentiality_rating, app.integrity_rating,
                   app.availability_rating, app.resilience_rating,
                   p.version as profile_version
            FROM evidence e
            JOIN evidence_field_link efl ON e.evidence_id = efl.evidence_id
            JOIN profile_field pf ON e.profile_field_id = pf.id
            JOIN profile p ON pf.profile_id = p.profile_id
            LEFT JOIN document d ON e.document_id = d.document_id
            LEFT JOIN application app ON e.app_id = app.app_id
            WHERE efl.link_status IN ('APPROVED', 'USER_ATTESTED')
            """);

        Map<String, Object> params = new HashMap<>();

        SqlFilterBuilder.addProfileVersionFilter(sqlBuilder, params, appId, "p");
        SqlFilterBuilder.addCriticalityFilter(sqlBuilder, params, criticality, "app.app_criticality_assessment");
        SqlFilterBuilder.addDomainFilter(sqlBuilder, params, domain, "pf.derived_from");
        SqlFilterBuilder.addFieldKeyFilter(sqlBuilder, params, fieldKey, "pf.field_key");
        SqlFilterBuilder.addSearchFilter(sqlBuilder, params, search, "e.uri", "d.title", "pf.field_key");
        SqlFilterBuilder.addOrderBy(sqlBuilder, "e.created_at DESC");
        SqlFilterBuilder.addPagination(sqlBuilder, params, limit, offset);

        return jdbc.query(sqlBuilder.toString(), params, this::mapKpiEvidenceSummary);
    }

    /**
     * Count compliant evidence items
     */
    public long countCompliantEvidence(String appId, String criticality, String domain, String fieldKey, String search) {
        StringBuilder sqlBuilder = new StringBuilder("""
            SELECT COUNT(DISTINCT e.evidence_id)
            FROM evidence e
            JOIN evidence_field_link efl ON e.evidence_id = efl.evidence_id
            JOIN profile_field pf ON e.profile_field_id = pf.id
            JOIN profile p ON pf.profile_id = p.profile_id
            LEFT JOIN document d ON e.document_id = d.document_id
            LEFT JOIN application app ON e.app_id = app.app_id
            WHERE efl.link_status IN ('APPROVED', 'USER_ATTESTED')
            """);

        Map<String, Object> params = new HashMap<>();

        SqlFilterBuilder.addProfileVersionFilter(sqlBuilder, params, appId, "p");
        SqlFilterBuilder.addCriticalityFilter(sqlBuilder, params, criticality, "app.app_criticality_assessment");
        SqlFilterBuilder.addDomainFilter(sqlBuilder, params, domain, "pf.derived_from");
        SqlFilterBuilder.addFieldKeyFilter(sqlBuilder, params, fieldKey, "pf.field_key");
        SqlFilterBuilder.addSearchFilter(sqlBuilder, params, search, "e.uri", "d.title", "pf.field_key");

        Long count = jdbc.queryForObject(sqlBuilder.toString(), params, Long.class);
        return count != null ? count : 0L;
    }

    /**
     * Find evidence by KPI state: PENDING REVIEW
     * Returns evidence that has pending review status but no approved/attested evidence for the same field
     */
    public List<KpiEvidenceSummary> findPendingReviewEvidence(
            String appId, String criticality, String domain, String fieldKey, String search, int limit, int offset) {
        StringBuilder sqlBuilder = new StringBuilder("""
            SELECT DISTINCT e.evidence_id, e.app_id, e.profile_field_id, e.claim_id, e.uri, e.type, e.status,
                   e.submitted_by, e.valid_from, e.valid_until, e.track_id, e.document_id, e.doc_version_id,
                   e.created_at, e.updated_at,
                   efl.link_status, efl.linked_by, efl.linked_at,
                   efl.reviewed_by, efl.reviewed_at, efl.review_comment,
                   d.title as document_title, d.source_type as document_source_type,
                   d.owners as document_owners, d.link_health as document_link_health,
                   pf.field_key, pf.derived_from,
                   app.name as app_name, app.product_owner, app.application_tier,
                   app.architecture_type, app.install_type, app.app_criticality_assessment,
                   app.security_rating, app.confidentiality_rating, app.integrity_rating,
                   app.availability_rating, app.resilience_rating,
                   p.version as profile_version
            FROM evidence e
            JOIN evidence_field_link efl ON e.evidence_id = efl.evidence_id
            JOIN profile_field pf ON e.profile_field_id = pf.id
            JOIN profile p ON pf.profile_id = p.profile_id
            LEFT JOIN document d ON e.document_id = d.document_id
            LEFT JOIN application app ON e.app_id = app.app_id
            WHERE efl.link_status IN ('PENDING_PO_REVIEW', 'PENDING_SME_REVIEW')
            AND NOT EXISTS (
                SELECT 1 FROM evidence e2
                JOIN evidence_field_link efl2 ON efl2.evidence_id = e2.evidence_id
                WHERE e2.profile_field_id = pf.id
                AND efl2.link_status IN ('APPROVED', 'USER_ATTESTED')
            )
            """);

        Map<String, Object> params = new HashMap<>();

        SqlFilterBuilder.addProfileVersionFilter(sqlBuilder, params, appId, "p");
        SqlFilterBuilder.addCriticalityFilter(sqlBuilder, params, criticality, "app.app_criticality_assessment");
        SqlFilterBuilder.addDomainFilter(sqlBuilder, params, domain, "pf.derived_from");
        SqlFilterBuilder.addFieldKeyFilter(sqlBuilder, params, fieldKey, "pf.field_key");
        SqlFilterBuilder.addSearchFilter(sqlBuilder, params, search, "e.uri", "d.title", "pf.field_key");
        SqlFilterBuilder.addOrderBy(sqlBuilder, "e.created_at DESC");
        SqlFilterBuilder.addPagination(sqlBuilder, params, limit, offset);

        return jdbc.query(sqlBuilder.toString(), params, this::mapKpiEvidenceSummary);
    }

    /**
     * Count pending review evidence items
     */
    public long countPendingReviewEvidence(String appId, String criticality, String domain, String fieldKey, String search) {
        StringBuilder sqlBuilder = new StringBuilder("""
            SELECT COUNT(DISTINCT e.evidence_id)
            FROM evidence e
            JOIN evidence_field_link efl ON e.evidence_id = efl.evidence_id
            JOIN profile_field pf ON e.profile_field_id = pf.id
            JOIN profile p ON pf.profile_id = p.profile_id
            LEFT JOIN document d ON e.document_id = d.document_id
            LEFT JOIN application app ON e.app_id = app.app_id
            WHERE efl.link_status IN ('PENDING_PO_REVIEW', 'PENDING_SME_REVIEW')
            AND NOT EXISTS (
                SELECT 1 FROM evidence e2
                JOIN evidence_field_link efl2 ON efl2.evidence_id = e2.evidence_id
                WHERE e2.profile_field_id = pf.id
                AND efl2.link_status IN ('APPROVED', 'USER_ATTESTED')
            )
            """);

        Map<String, Object> params = new HashMap<>();

        SqlFilterBuilder.addProfileVersionFilter(sqlBuilder, params, appId, "p");
        SqlFilterBuilder.addCriticalityFilter(sqlBuilder, params, criticality, "app.app_criticality_assessment");
        SqlFilterBuilder.addDomainFilter(sqlBuilder, params, domain, "pf.derived_from");
        SqlFilterBuilder.addFieldKeyFilter(sqlBuilder, params, fieldKey, "pf.field_key");
        SqlFilterBuilder.addSearchFilter(sqlBuilder, params, search, "e.uri", "d.title", "pf.field_key");

        Long count = jdbc.queryForObject(sqlBuilder.toString(), params, Long.class);
        return count != null ? count : 0L;
    }

    /**
     * Find profile fields with MISSING EVIDENCE
     * Returns profile fields that have no approved/attested evidence
     */
    public List<Map<String, Object>> findMissingEvidenceFields(
            String appId, String criticality, String domain, String fieldKey, String search, int limit, int offset) {
        StringBuilder sqlBuilder = new StringBuilder("""
            SELECT DISTINCT pf.id as profile_field_id, pf.field_key, pf.derived_from,
                   app.app_id, app.name as app_name, app.product_owner, app.application_tier,
                   app.architecture_type, app.install_type, app.app_criticality_assessment,
                   app.security_rating, app.confidentiality_rating, app.integrity_rating,
                   app.availability_rating, app.resilience_rating,
                   p.version as profile_version
            FROM profile_field pf
            JOIN profile p ON pf.profile_id = p.profile_id
            LEFT JOIN application app ON p.app_id = app.app_id
            WHERE NOT EXISTS (
                SELECT 1 FROM evidence e
                JOIN evidence_field_link efl ON e.evidence_id = efl.evidence_id
                WHERE e.profile_field_id = pf.id
                AND efl.link_status IN ('APPROVED', 'USER_ATTESTED')
            )
            """);

        Map<String, Object> params = new HashMap<>();

        SqlFilterBuilder.addProfileVersionFilter(sqlBuilder, params, appId, "p");
        SqlFilterBuilder.addCriticalityFilter(sqlBuilder, params, criticality, "app.app_criticality_assessment");
        SqlFilterBuilder.addDomainFilter(sqlBuilder, params, domain, "pf.derived_from");
        SqlFilterBuilder.addFieldKeyFilter(sqlBuilder, params, fieldKey, "pf.field_key");
        SqlFilterBuilder.addSearchFilter(sqlBuilder, params, search, "pf.field_key");
        SqlFilterBuilder.addOrderBy(sqlBuilder, "pf.field_key");
        SqlFilterBuilder.addPagination(sqlBuilder, params, limit, offset);

        return jdbc.queryForList(sqlBuilder.toString(), params);
    }

    /**
     * Count missing evidence fields
     */
    public long countMissingEvidenceFields(String appId, String criticality, String domain, String fieldKey, String search) {
        StringBuilder sqlBuilder = new StringBuilder("""
            SELECT COUNT(DISTINCT pf.id)
            FROM profile_field pf
            JOIN profile p ON pf.profile_id = p.profile_id
            LEFT JOIN application app ON p.app_id = app.app_id
            WHERE NOT EXISTS (
                SELECT 1 FROM evidence e
                JOIN evidence_field_link efl ON e.evidence_id = efl.evidence_id
                WHERE e.profile_field_id = pf.id
                AND efl.link_status IN ('APPROVED', 'USER_ATTESTED')
            )
            """);

        Map<String, Object> params = new HashMap<>();

        SqlFilterBuilder.addProfileVersionFilter(sqlBuilder, params, appId, "p");
        SqlFilterBuilder.addCriticalityFilter(sqlBuilder, params, criticality, "app.app_criticality_assessment");
        SqlFilterBuilder.addDomainFilter(sqlBuilder, params, domain, "pf.derived_from");
        SqlFilterBuilder.addFieldKeyFilter(sqlBuilder, params, fieldKey, "pf.field_key");
        SqlFilterBuilder.addSearchFilter(sqlBuilder, params, search, "pf.field_key");

        Long count = jdbc.queryForObject(sqlBuilder.toString(), params, Long.class);
        return count != null ? count : 0L;
    }

    /**
     * Find evidence by KPI state: RISK BLOCKED
     * Returns evidence with assigned risks but no approved/attested evidence for the same field
     */
    public List<RiskBlockedItem> findRiskBlockedItems(
            String appId, String criticality, String domain, String fieldKey, String search, int limit, int offset) {
        StringBuilder sqlBuilder = new StringBuilder("""
            SELECT DISTINCT r.risk_id, r.status as risk_status, r.assigned_sme,
                   r.created_at, r.updated_at, r.triggering_evidence_id,
                   r.risk_title as title, r.risk_description as hypothesis,
                   pf.field_key, pf.field_key as control_field, pf.derived_from,
                   app.app_id, app.name as app_name, app.product_owner, app.application_tier,
                   app.architecture_type, app.install_type, app.app_criticality_assessment,
                   app.security_rating, app.confidentiality_rating, app.integrity_rating,
                   app.availability_rating, app.resilience_rating
            FROM risk_story r
            JOIN profile_field pf ON r.profile_field_id = pf.id
            JOIN profile p ON pf.profile_id = p.profile_id
            LEFT JOIN application app ON p.app_id = app.app_id
            WHERE NOT EXISTS (
                SELECT 1 FROM evidence e
                JOIN evidence_field_link efl ON e.evidence_id = efl.evidence_id
                WHERE e.profile_field_id = pf.id
                AND efl.link_status IN ('APPROVED', 'USER_ATTESTED')
            )
            """);

        Map<String, Object> params = new HashMap<>();

        SqlFilterBuilder.addProfileVersionFilter(sqlBuilder, params, appId, "p");
        SqlFilterBuilder.addCriticalityFilter(sqlBuilder, params, criticality, "app.app_criticality_assessment");
        SqlFilterBuilder.addDomainFilter(sqlBuilder, params, domain, "pf.derived_from");
        SqlFilterBuilder.addFieldKeyFilter(sqlBuilder, params, fieldKey, "pf.field_key");
        SqlFilterBuilder.addSearchFilter(sqlBuilder, params, search, "r.risk_title", "pf.field_key");
        SqlFilterBuilder.addOrderBy(sqlBuilder, "r.created_at DESC");
        SqlFilterBuilder.addPagination(sqlBuilder, params, limit, offset);

        return jdbc.query(sqlBuilder.toString(), params, this::mapRiskBlockedItem);
    }

    /**
     * Count risk-blocked items
     */
    public long countRiskBlockedItems(String appId, String criticality, String domain, String fieldKey, String search) {
        StringBuilder sqlBuilder = new StringBuilder("""
            SELECT COUNT(DISTINCT r.risk_id)
            FROM risk_story r
            JOIN profile_field pf ON r.profile_field_id = pf.id
            JOIN profile p ON pf.profile_id = p.profile_id
            LEFT JOIN application app ON p.app_id = app.app_id
            WHERE NOT EXISTS (
                SELECT 1 FROM evidence e
                JOIN evidence_field_link efl ON e.evidence_id = efl.evidence_id
                WHERE e.profile_field_id = pf.id
                AND efl.link_status IN ('APPROVED', 'USER_ATTESTED')
            )
            """);

        Map<String, Object> params = new HashMap<>();

        SqlFilterBuilder.addProfileVersionFilter(sqlBuilder, params, appId, "p");
        SqlFilterBuilder.addCriticalityFilter(sqlBuilder, params, criticality, "app.app_criticality_assessment");
        SqlFilterBuilder.addDomainFilter(sqlBuilder, params, domain, "pf.derived_from");
        SqlFilterBuilder.addFieldKeyFilter(sqlBuilder, params, fieldKey, "pf.field_key");
        SqlFilterBuilder.addSearchFilter(sqlBuilder, params, search, "r.risk_title", "pf.field_key");

        Long count = jdbc.queryForObject(sqlBuilder.toString(), params, Long.class);
        return count != null ? count : 0L;
    }

    // ========== MAPPERS ==========

    private KpiEvidenceSummary mapKpiEvidenceSummary(ResultSet rs, int rowNum) throws SQLException {
        return new KpiEvidenceSummary(
            rs.getString("evidence_id"),
            rs.getString("app_id"),
            rs.getString("profile_field_id"),
            rs.getString("claim_id"),
            rs.getString("uri"),
            rs.getString("type"),
            rs.getString("status"),
            rs.getString("submitted_by"),
            rs.getObject("valid_from", OffsetDateTime.class),
            rs.getObject("valid_until", OffsetDateTime.class),
            rs.getString("track_id"),
            rs.getString("document_id"),
            rs.getString("doc_version_id"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class),
            // EvidenceFieldLink metadata
            EvidenceFieldLinkStatus.valueOf(rs.getString("link_status")),
            rs.getString("linked_by"),
            rs.getObject("linked_at", OffsetDateTime.class),
            rs.getString("reviewed_by"),
            rs.getObject("reviewed_at", OffsetDateTime.class),
            rs.getString("review_comment"),
            // Document source information
            rs.getString("document_title"),
            rs.getString("document_source_type"),
            rs.getString("document_owners"),
            (Integer) rs.getObject("document_link_health"),
            // Profile field context
            rs.getString("field_key"),
            rs.getString("derived_from"),
            extractDomainRating(rs, rs.getString("derived_from")),
            // Application context
            rs.getString("app_name"),
            rs.getString("product_owner"),
            rs.getString("application_tier"),
            rs.getString("architecture_type"),
            rs.getString("install_type"),
            rs.getString("app_criticality_assessment"),
            // Profile version
            rs.getObject("profile_version", Integer.class)
        );
    }

    /**
     * Extract domain rating value based on derivedFrom field
     */
    private String extractDomainRating(ResultSet rs, String derivedFrom) throws SQLException {
        if (derivedFrom == null) return null;

        // Try to get the rating value for security, confidentiality, etc.
        try {
            return rs.getString(derivedFrom);
        } catch (SQLException e) {
            // Column doesn't exist, return null
            return null;
        }
    }

    private RiskBlockedItem mapRiskBlockedItem(ResultSet rs, int rowNum) throws SQLException {
        return new RiskBlockedItem(
            rs.getString("risk_id"),
            rs.getString("app_id"),
            rs.getString("field_key"),
            rs.getString("risk_status"),
            rs.getString("assigned_sme"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class),
            rs.getString("triggering_evidence_id"),
            rs.getString("title"),
            rs.getString("hypothesis"),
            // Application context
            rs.getString("app_name"),
            rs.getString("product_owner"),
            rs.getString("application_tier"),
            rs.getString("architecture_type"),
            rs.getString("install_type"),
            rs.getString("app_criticality_assessment"),
            // Profile field context
            rs.getString("control_field"),
            rs.getString("derived_from"),
            extractDomainRating(rs, rs.getString("derived_from"))
        );
    }
}
