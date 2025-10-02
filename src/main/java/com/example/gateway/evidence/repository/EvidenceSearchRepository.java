package com.example.gateway.evidence.repository;

import com.example.gateway.evidence.dto.EnhancedEvidenceSummary;
import com.example.gateway.evidence.dto.EvidenceSearchRequest;
import com.example.gateway.evidence.model.EvidenceFieldLinkStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Repository for evidence search and filtering operations.
 * Handles complex search queries with multiple filter criteria.
 */
@Repository
public class EvidenceSearchRepository {

    private static final Logger log = LoggerFactory.getLogger(EvidenceSearchRepository.class);

    private final NamedParameterJdbcTemplate jdbc;

    public EvidenceSearchRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Search evidence with multiple filter criteria
     */
    public List<EnhancedEvidenceSummary> searchEvidence(
            String linkStatus, String appId, String fieldKey, String assignedPo,
            String assignedSme, String evidenceStatus, String documentSourceType,
            int limit, int offset) {

        StringBuilder sqlBuilder = new StringBuilder("""
            SELECT e.evidence_id, e.app_id, e.profile_field_id, e.claim_id, e.uri, e.type, e.status,
                   e.submitted_by, e.valid_from, e.valid_until, e.track_id, e.document_id, e.doc_version_id,
                   e.created_at, e.updated_at,
                   efl.link_status, efl.linked_by, efl.linked_at,
                   efl.reviewed_by, efl.reviewed_at, efl.review_comment,
                   d.title as document_title, d.source_type as document_source_type,
                   d.owners as document_owners, d.link_health as document_link_health
            FROM evidence e
            JOIN evidence_field_link efl ON e.evidence_id = efl.evidence_id
            JOIN profile_field pf ON e.profile_field_id = pf.id
            JOIN profile p ON pf.profile_id = p.profile_id
            LEFT JOIN document d ON e.document_id = d.document_id
            LEFT JOIN application app ON e.app_id = app.app_id
            WHERE 1=1
            """);

        Map<String, Object> params = new HashMap<>();

        // Add profile version filtering (KPI-style logic)
        if (appId != null && !appId.trim().isEmpty()) {
            sqlBuilder.append(" AND p.app_id = :appId");
            sqlBuilder.append(" AND p.version = (SELECT MAX(version) FROM profile WHERE app_id = :appId)");
            params.put("appId", appId.trim());
        } else {
            // For portfolio-wide searches, ensure we only get latest versions for each app
            sqlBuilder.append(" AND p.version = (SELECT MAX(version) FROM profile p2 WHERE p2.app_id = p.app_id)");
        }

        // Add dynamic filters
        if (linkStatus != null && !linkStatus.trim().isEmpty()) {
            sqlBuilder.append(" AND efl.link_status = :linkStatus");
            params.put("linkStatus", linkStatus.trim());
        }

        if (fieldKey != null && !fieldKey.trim().isEmpty()) {
            sqlBuilder.append(" AND pf.field_key = :fieldKey");
            params.put("fieldKey", fieldKey.trim());
        }

        if (assignedPo != null && !assignedPo.trim().isEmpty()) {
            sqlBuilder.append(" AND app.product_owner = :assignedPo");
            params.put("assignedPo", assignedPo.trim());
        }

        if (assignedSme != null && !assignedSme.trim().isEmpty()) {
            // For SME assignment, join with risk_story table using proper profile version filtering
            sqlBuilder.append(" AND EXISTS (SELECT 1 FROM risk_story rs WHERE rs.field_key = pf.field_key AND rs.app_id = p.app_id AND rs.assigned_sme = :assignedSme)");
            params.put("assignedSme", assignedSme.trim());
        }

        // Evidence status filter removed - field deprecated
        if (evidenceStatus != null && !evidenceStatus.trim().isEmpty()) {
            log.warn("Evidence status filter '{}' ignored - status field deprecated", evidenceStatus.trim());
        }

        if (documentSourceType != null && !documentSourceType.trim().isEmpty()) {
            sqlBuilder.append(" AND d.source_type = :documentSourceType");
            params.put("documentSourceType", documentSourceType.trim());
        }

        sqlBuilder.append(" ORDER BY e.created_at DESC LIMIT :limit OFFSET :offset");
        params.put("limit", limit);
        params.put("offset", offset);

        return jdbc.query(sqlBuilder.toString(), params, this::mapEnhancedEvidenceSummary);
    }

    /**
     * Search evidence with enhanced workbench view
     */
    public List<Map<String, Object>> searchWorkbenchEvidence(EvidenceSearchRequest request) {
        StringBuilder sqlBuilder = new StringBuilder("""
            SELECT
                e.evidence_id,
                e.doc_version_id,
                e.valid_from,
                e.created_at,
                app.app_id,
                app.name AS app_name,
                app.app_criticality_assessment AS app_criticality,
                app.application_type,
                app.architecture_type,
                app.install_type,
                app.application_tier,
                pf.id as profile_field_id,
                pf.field_key,
                pf.field_key AS field_label,
                pf.value,
                efl.link_status AS approval_status,
                efl.reviewed_by AS assigned_reviewer,
                e.created_at AS submitted_date,
                e.submitted_by,
                e.valid_until,
                e.uri,
                (SELECT COUNT(*) FROM risk_story rs WHERE rs.triggering_evidence_id = e.evidence_id) AS risk_count
            FROM
                evidence e
                JOIN evidence_field_link efl ON e.evidence_id = efl.evidence_id
                JOIN profile_field pf ON efl.profile_field_id = pf.id
                JOIN profile p ON pf.profile_id = p.profile_id
                LEFT JOIN application app ON e.app_id = app.app_id
                LEFT JOIN document d ON e.document_id = d.document_id
            WHERE 1=1
            """);

        MapSqlParameterSource params = new MapSqlParameterSource();

        // Add profile version filtering (KPI-style logic)
        if (request.getAppId() != null) {
            sqlBuilder.append(" AND p.app_id = :appId");
            sqlBuilder.append(" AND p.version = (SELECT MAX(version) FROM profile WHERE app_id = :appId)");
            params.addValue("appId", request.getAppId());
        } else {
            // For portfolio-wide searches, ensure we only get latest versions for each app
            sqlBuilder.append(" AND p.version = (SELECT MAX(version) FROM profile p2 WHERE p2.app_id = p.app_id)");
        }

        // Add dynamic filters
        if (request.getStatus() != null) {
            sqlBuilder.append(" AND e.status = :status");
            params.addValue("status", request.getStatus());
        }
        if (request.getApprovalStatus() != null) {
            sqlBuilder.append(" AND efl.link_status = :approvalStatus");
            params.addValue("approvalStatus", request.getApprovalStatus());
        }
        if (request.getCriticality() != null) {
            sqlBuilder.append(" AND app.app_criticality_assessment = :criticality");
            params.addValue("criticality", request.getCriticality());
        }
        if (request.getApplicationType() != null) {
            sqlBuilder.append(" AND app.application_type = :applicationType");
            params.addValue("applicationType", request.getApplicationType());
        }
        if (request.getArchitectureType() != null) {
            sqlBuilder.append(" AND app.architecture_type = :architectureType");
            params.addValue("architectureType", request.getArchitectureType());
        }
        if (request.getInstallType() != null) {
            sqlBuilder.append(" AND app.install_type = :installType");
            params.addValue("installType", request.getInstallType());
        }
        if (request.getFieldKey() != null) {
            sqlBuilder.append(" AND pf.field_key = :fieldKey");
            params.addValue("fieldKey", request.getFieldKey());
        }
        if (request.getAssignedReviewer() != null) {
            sqlBuilder.append(" AND efl.reviewed_by = :assignedReviewer");
            params.addValue("assignedReviewer", request.getAssignedReviewer());
        }
        if (request.getSubmittedBy() != null) {
            sqlBuilder.append(" AND e.submitted_by = :submittedBy");
            params.addValue("submittedBy", request.getSubmittedBy());
        }
        if (request.getDomain() != null) {
            sqlBuilder.append(" AND pf.derived_from LIKE :domain || '_rating'");
            params.addValue("domain", request.getDomain());
        }
        if (request.getSearch() != null && !request.getSearch().isBlank()) {
            sqlBuilder.append("""
                 AND (
                    app.name ILIKE '%' || :search || '%'
                    OR pf.field_key ILIKE '%' || :search || '%'
                    OR app.app_id ILIKE '%' || :search || '%'
                )
                """);
            params.addValue("search", request.getSearch());
        }

        sqlBuilder.append(" ORDER BY e.created_at DESC");
        sqlBuilder.append(" LIMIT :limit OFFSET :offset");
        params.addValue("limit", request.getLimit());
        params.addValue("offset", request.getOffset());

        return jdbc.queryForList(sqlBuilder.toString(), params);
    }

    // ========== MAPPERS ==========

    private EnhancedEvidenceSummary mapEnhancedEvidenceSummary(ResultSet rs, int rowNum) throws SQLException {
        String linkStatusStr = rs.getString("link_status");
        EvidenceFieldLinkStatus linkStatus = null;
        if (linkStatusStr != null) {
            try {
                linkStatus = EvidenceFieldLinkStatus.valueOf(linkStatusStr);
            } catch (IllegalArgumentException e) {
                log.warn("Unknown link_status value: {}", linkStatusStr);
            }
        }

        return new EnhancedEvidenceSummary(
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
            linkStatus,
            rs.getString("linked_by"),
            rs.getObject("linked_at", OffsetDateTime.class),
            rs.getString("reviewed_by"),
            rs.getObject("reviewed_at", OffsetDateTime.class),
            rs.getString("review_comment"),
            // Document source information
            rs.getString("document_title"),
            rs.getString("document_source_type"),
            rs.getString("document_owners"),
            (Integer) rs.getObject("document_link_health")
        );
    }
}
