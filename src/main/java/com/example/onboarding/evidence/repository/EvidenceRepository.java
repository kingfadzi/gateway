package com.example.onboarding.evidence.repository;

import com.example.onboarding.evidence.dto.*;
import com.example.onboarding.evidence.model.EvidenceFieldLinkStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class EvidenceRepository {
    
    private static final Logger log = LoggerFactory.getLogger(EvidenceRepository.class);
    
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    
    public EvidenceRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Create new evidence
     */
    public String createEvidence(String appId, String profileFieldId, String uri, String type, 
                                String sourceSystem, String submittedBy, OffsetDateTime validFrom, 
                                OffsetDateTime validUntil, String relatedEvidenceFields, String trackId, 
                                String documentId, String docVersionId) {
        String evidenceId = "ev_" + UUID.randomUUID().toString().replace("-", "");
        
        String sql = """
            INSERT INTO evidence (
                evidence_id, app_id, profile_field_id, uri, type, source_system, 
                submitted_by, valid_from, valid_until, related_evidence_fields, track_id, 
                document_id, doc_version_id, created_at, updated_at
            ) VALUES (
                :evidenceId, :appId, :profileFieldId, :uri, :type, :sourceSystem,
                :submittedBy, :validFrom, :validUntil, :relatedEvidenceFields, :trackId,
                :documentId, :docVersionId, now(), now()
            )
            """;
        
        jdbc.update(sql, new MapSqlParameterSource()
            .addValue("evidenceId", evidenceId)
            .addValue("appId", appId)
            .addValue("profileFieldId", profileFieldId)
            .addValue("uri", uri)
            .addValue("type", type)
            .addValue("sourceSystem", sourceSystem)
            .addValue("submittedBy", submittedBy)
            .addValue("validFrom", validFrom)
            .addValue("validUntil", validUntil)
            .addValue("relatedEvidenceFields", relatedEvidenceFields)
            .addValue("trackId", trackId)
            .addValue("documentId", documentId)
            .addValue("docVersionId", docVersionId)
        );
        
        log.debug("Created evidence {} for app {} profile_field {}", evidenceId, appId, profileFieldId);
        return evidenceId;
    }
    
    /**
     * Update existing evidence
     */
    public boolean updateEvidence(String evidenceId, String uri, String type, String sourceSystem,
                                 String submittedBy, OffsetDateTime validFrom, OffsetDateTime validUntil,
                                 String status, String reviewedBy, String relatedEvidenceFields, String documentId, 
                                 String docVersionId) {
        String sql = """
            UPDATE evidence 
            SET uri = COALESCE(:uri, uri),
                type = COALESCE(:type, type),
                source_system = COALESCE(:sourceSystem, source_system),
                submitted_by = COALESCE(:submittedBy, submitted_by),
                valid_from = COALESCE(:validFrom, valid_from),
                valid_until = COALESCE(:validUntil, valid_until),
                status = COALESCE(:status, status),
                reviewed_by = COALESCE(:reviewedBy, reviewed_by),
                reviewed_at = CASE WHEN :reviewedBy IS NOT NULL THEN now() ELSE reviewed_at END,
                related_evidence_fields = COALESCE(:relatedEvidenceFields, related_evidence_fields),
                document_id = COALESCE(:documentId, document_id),
                doc_version_id = COALESCE(:docVersionId, doc_version_id),
                updated_at = now()
            WHERE evidence_id = :evidenceId
            """;
        
        int updated = jdbc.update(sql, new MapSqlParameterSource()
            .addValue("evidenceId", evidenceId)
            .addValue("uri", uri)
            .addValue("type", type)
            .addValue("sourceSystem", sourceSystem)
            .addValue("submittedBy", submittedBy)
            .addValue("validFrom", validFrom)
            .addValue("validUntil", validUntil)
            .addValue("status", status)
            .addValue("reviewedBy", reviewedBy)
            .addValue("relatedEvidenceFields", relatedEvidenceFields)
            .addValue("documentId", documentId)
            .addValue("docVersionId", docVersionId)
        );
        
        log.debug("Updated evidence {} (status={})", evidenceId, status);
        return updated > 0;
    }
    
    /**
     * Find evidence by ID
     */
    public Optional<Evidence> findEvidenceById(String evidenceId) {
        String sql = """
            SELECT * FROM evidence WHERE evidence_id = :evidenceId
            """;
        
        try {
            Evidence evidence = jdbc.queryForObject(sql, Map.of("evidenceId", evidenceId), this::mapEvidence);
            return Optional.of(evidence);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
    
    /**
     * Find enhanced evidence by app with EvidenceFieldLink metadata and document source info
     * Uses KPI-style profile version filtering
     */
    public List<EnhancedEvidenceSummary> findEvidenceByApp(String appId, int limit, int offset) {
        String sql = """
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
            WHERE p.app_id = :appId
            AND p.version = (SELECT MAX(version) FROM profile WHERE app_id = :appId)
            ORDER BY e.created_at DESC
            LIMIT :limit OFFSET :offset
            """;

        return jdbc.query(sql, Map.of("appId", appId, "limit", limit, "offset", offset),
            this::mapEnhancedEvidenceSummary);
    }
    
    /**
     * Count evidence by app
     */
    public long countEvidenceByApp(String appId) {
        String sql = "SELECT COUNT(*) FROM evidence WHERE app_id = :appId";
        Integer count = jdbc.queryForObject(sql, Map.of("appId", appId), Integer.class);
        return count != null ? count : 0;
    }
    
    /**
     * Find enhanced evidence by profile field with EvidenceFieldLink metadata and document source info
     * Uses KPI-style profile version filtering
     */
    public List<EnhancedEvidenceSummary> findEvidenceByProfileField(String profileFieldId, int limit, int offset) {
        String sql = """
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
            WHERE e.profile_field_id = :profileFieldId
            AND p.version = (SELECT MAX(version) FROM profile p2 WHERE p2.app_id = p.app_id)
            ORDER BY e.created_at DESC
            LIMIT :limit OFFSET :offset
            """;

        return jdbc.query(sql, Map.of("profileFieldId", profileFieldId, "limit", limit, "offset", offset),
            this::mapEnhancedEvidenceSummary);
    }
    
    
    /**
     * Find enhanced evidence by claim with EvidenceFieldLink metadata and document source info
     * Uses KPI-style profile version filtering
     */
    public List<EnhancedEvidenceSummary> findEvidenceByClaim(String claimId, int limit, int offset) {
        String sql = """
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
            WHERE e.claim_id = :claimId
            AND p.version = (SELECT MAX(version) FROM profile p2 WHERE p2.app_id = p.app_id)
            ORDER BY e.created_at DESC
            LIMIT :limit OFFSET :offset
            """;

        return jdbc.query(sql, Map.of("claimId", claimId, "limit", limit, "offset", offset),
            this::mapEnhancedEvidenceSummary);
    }
    
    /**
     * Find enhanced evidence by track with EvidenceFieldLink metadata and document source info
     * Uses KPI-style profile version filtering
     */
    public List<EnhancedEvidenceSummary> findEvidenceByTrack(String trackId, int limit, int offset) {
        String sql = """
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
            WHERE e.track_id = :trackId
            AND p.version = (SELECT MAX(version) FROM profile p2 WHERE p2.app_id = p.app_id)
            ORDER BY e.created_at DESC
            LIMIT :limit OFFSET :offset
            """;

        return jdbc.query(sql, Map.of("trackId", trackId, "limit", limit, "offset", offset),
            this::mapEnhancedEvidenceSummary);
    }
    
    /**
     * Search evidence with multiple filters using KPI-style query logic with profile version filtering
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
                   d.owners as document_owners, d.link_health as document_link_health,
                   pf.field_key, app.product_owner
            FROM evidence e
            JOIN evidence_field_link efl ON e.evidence_id = efl.evidence_id
            JOIN profile_field pf ON e.profile_field_id = pf.id
            JOIN profile p ON pf.profile_id = p.profile_id
            LEFT JOIN document d ON e.document_id = d.document_id
            LEFT JOIN application app ON e.app_id = app.app_id
            WHERE 1=1
            """);

        java.util.Map<String, Object> params = new java.util.HashMap<>();

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
                pf.field_key AS field_label, -- Corrected column
                pf.value, -- Assuming TTL info is in the 'value' jsonb for a field
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

        java.util.Map<String, Object> params = new java.util.HashMap<>();

        // Add profile version filtering (KPI-style logic)
        if (appId != null && !appId.trim().isEmpty()) {
            sqlBuilder.append(" AND p.app_id = :appId");
            sqlBuilder.append(" AND p.version = (SELECT MAX(version) FROM profile WHERE app_id = :appId)");
            params.put("appId", appId.trim());
        } else {
            // For portfolio-wide searches, ensure we only get latest versions for each app
            sqlBuilder.append(" AND p.version = (SELECT MAX(version) FROM profile p2 WHERE p2.app_id = p.app_id)");
        }

        // Add criticality filter
        if (criticality != null && !criticality.trim().isEmpty()) {
            String[] criticalityValues = criticality.split(",");
            sqlBuilder.append(" AND app.app_criticality_assessment IN (");
            for (int i = 0; i < criticalityValues.length; i++) {
                sqlBuilder.append(":criticality").append(i);
                if (i < criticalityValues.length - 1) {
                    sqlBuilder.append(", ");
                }
                params.put("criticality" + i, criticalityValues[i].trim());
            }
            sqlBuilder.append(")");
        }

        // Add domain filter (matches derivedFrom pattern)
        if (domain != null && !domain.trim().isEmpty()) {
            sqlBuilder.append(" AND pf.derived_from = :derivedFrom");
            params.put("derivedFrom", domain.trim());
        }

        // Add field key filter
        if (fieldKey != null && !fieldKey.trim().isEmpty()) {
            sqlBuilder.append(" AND pf.field_key = :fieldKey");
            params.put("fieldKey", fieldKey.trim());
        }

        // Add text search filter
        if (search != null && !search.trim().isEmpty()) {
            sqlBuilder.append(" AND (e.uri ILIKE :search OR d.title ILIKE :search OR pf.field_key ILIKE :search)");
            params.put("search", "%" + search.trim() + "%");
        }

        sqlBuilder.append(" ORDER BY e.created_at DESC LIMIT :limit OFFSET :offset");
        params.put("limit", limit);
        params.put("offset", offset);

        return jdbc.query(sqlBuilder.toString(), params, this::mapKpiEvidenceSummary);
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

        java.util.Map<String, Object> params = new java.util.HashMap<>();

        // Add profile version filtering (KPI-style logic)
        if (appId != null && !appId.trim().isEmpty()) {
            sqlBuilder.append(" AND p.app_id = :appId");
            sqlBuilder.append(" AND p.version = (SELECT MAX(version) FROM profile WHERE app_id = :appId)");
            params.put("appId", appId.trim());
        } else {
            // For portfolio-wide searches, ensure we only get latest versions for each app
            sqlBuilder.append(" AND p.version = (SELECT MAX(version) FROM profile p2 WHERE p2.app_id = p.app_id)");
        }

        // Add criticality filter
        if (criticality != null && !criticality.trim().isEmpty()) {
            String[] criticalityValues = criticality.split(",");
            sqlBuilder.append(" AND app.app_criticality_assessment IN (");
            for (int i = 0; i < criticalityValues.length; i++) {
                sqlBuilder.append(":criticality").append(i);
                if (i < criticalityValues.length - 1) {
                    sqlBuilder.append(", ");
                }
                params.put("criticality" + i, criticalityValues[i].trim());
            }
            sqlBuilder.append(")");
        }

        // Add domain filter (matches derivedFrom pattern)
        if (domain != null && !domain.trim().isEmpty()) {
            sqlBuilder.append(" AND pf.derived_from = :derivedFrom");
            params.put("derivedFrom", domain.trim());
        }

        // Add field key filter
        if (fieldKey != null && !fieldKey.trim().isEmpty()) {
            sqlBuilder.append(" AND pf.field_key = :fieldKey");
            params.put("fieldKey", fieldKey.trim());
        }

        // Add text search filter
        if (search != null && !search.trim().isEmpty()) {
            sqlBuilder.append(" AND (e.uri ILIKE :search OR d.title ILIKE :search OR pf.field_key ILIKE :search)");
            params.put("search", "%" + search.trim() + "%");
        }

        sqlBuilder.append(" ORDER BY e.created_at DESC LIMIT :limit OFFSET :offset");
        params.put("limit", limit);
        params.put("offset", offset);

        return jdbc.query(sqlBuilder.toString(), params, this::mapKpiEvidenceSummary);
    }

    /**
     * Find profile fields by KPI state: MISSING EVIDENCE
     * Returns profile fields that have no evidence at all
     * Note: This returns profile field info since there's no evidence to return
     */
    public List<Map<String, Object>> findMissingEvidenceFields(
            String appId, String criticality, String domain, String fieldKey, String search, int limit, int offset) {
        StringBuilder sqlBuilder = new StringBuilder("""
            SELECT pf.id as profile_field_id, pf.field_key, p.app_id,
                   app.name as app_name, app.product_owner,
                   p.profile_id, p.version
            FROM profile p
            JOIN profile_field pf ON p.profile_id = pf.profile_id
            LEFT JOIN application app ON p.app_id = app.app_id
            WHERE NOT EXISTS (
                SELECT 1 FROM evidence e
                WHERE e.profile_field_id = pf.id
            )
            """);

        java.util.Map<String, Object> params = new java.util.HashMap<>();

        // Add profile version filtering (KPI-style logic)
        if (appId != null && !appId.trim().isEmpty()) {
            sqlBuilder.append(" AND p.app_id = :appId");
            sqlBuilder.append(" AND p.version = (SELECT MAX(version) FROM profile WHERE app_id = :appId)");
            params.put("appId", appId.trim());
        } else {
            // For portfolio-wide searches, ensure we only get latest versions for each app
            sqlBuilder.append(" AND p.version = (SELECT MAX(version) FROM profile p2 WHERE p2.app_id = p.app_id)");
        }

        // Add criticality filter
        if (criticality != null && !criticality.trim().isEmpty()) {
            String[] criticalityValues = criticality.split(",");
            sqlBuilder.append(" AND app.app_criticality_assessment IN (");
            for (int i = 0; i < criticalityValues.length; i++) {
                sqlBuilder.append(":criticality").append(i);
                if (i < criticalityValues.length - 1) {
                    sqlBuilder.append(", ");
                }
                params.put("criticality" + i, criticalityValues[i].trim());
            }
            sqlBuilder.append(")");
        }

        // Add domain filter (matches derivedFrom pattern)
        if (domain != null && !domain.trim().isEmpty()) {
            sqlBuilder.append(" AND pf.derived_from = :derivedFrom");
            params.put("derivedFrom", domain.trim());
        }

        // Add field key filter
        if (fieldKey != null && !fieldKey.trim().isEmpty()) {
            sqlBuilder.append(" AND pf.field_key = :fieldKey");
            params.put("fieldKey", fieldKey.trim());
        }

        // Add text search filter (for missing evidence, search only field_key and app_name)
        if (search != null && !search.trim().isEmpty()) {
            sqlBuilder.append(" AND (pf.field_key ILIKE :search OR app.name ILIKE :search)");
            params.put("search", "%" + search.trim() + "%");
        }

        sqlBuilder.append(" ORDER BY pf.field_key LIMIT :limit OFFSET :offset");
        params.put("limit", limit);
        params.put("offset", offset);

        return jdbc.queryForList(sqlBuilder.toString(), params);
    }

    /**
     * Find risks by KPI state: RISK BLOCKED
     * Returns risks that are in open states (PENDING_SME_REVIEW, UNDER_REVIEW, OPEN)
     */
    public List<RiskBlockedItem> findRiskBlockedItems(
            String appId, String criticality, String domain, String fieldKey, String search, int limit, int offset) {
        StringBuilder sqlBuilder = new StringBuilder("""
            SELECT DISTINCT rs.risk_id, rs.app_id, rs.field_key, rs.status as risk_status,
                   rs.assigned_sme, rs.created_at, rs.updated_at,
                   rs.triggering_evidence_id, rs.title, rs.hypothesis,
                   app.name as app_name, app.product_owner,
                   app.application_tier, app.architecture_type,
                   app.install_type, app.app_criticality_assessment,
                   app.security_rating, app.confidentiality_rating, app.integrity_rating,
                   app.availability_rating, app.resilience_rating,
                   pf.field_key as control_field, pf.derived_from
            FROM risk_story rs
            LEFT JOIN application app ON rs.app_id = app.app_id
            LEFT JOIN profile p ON rs.app_id = p.app_id
                AND p.version = (SELECT MAX(version) FROM profile p2 WHERE p2.app_id = rs.app_id)
            LEFT JOIN profile_field pf ON rs.field_key = pf.field_key
                AND pf.profile_id = p.profile_id
            WHERE rs.status IN ('PENDING_SME_REVIEW', 'UNDER_REVIEW', 'OPEN')
            """);

        java.util.Map<String, Object> params = new java.util.HashMap<>();

        // Add profile version filtering (KPI-style logic)
        if (appId != null && !appId.trim().isEmpty()) {
            sqlBuilder.append(" AND rs.app_id = :appId");
            sqlBuilder.append(" AND p.version = (SELECT MAX(version) FROM profile WHERE app_id = :appId)");
            params.put("appId", appId.trim());
        } else {
            // For portfolio-wide searches, ensure we only get latest versions for each app
            sqlBuilder.append(" AND p.version = (SELECT MAX(version) FROM profile p2 WHERE p2.app_id = p.app_id)");
        }

        // Add criticality filter
        if (criticality != null && !criticality.trim().isEmpty()) {
            String[] criticalityValues = criticality.split(",");
            sqlBuilder.append(" AND app.app_criticality_assessment IN (");
            for (int i = 0; i < criticalityValues.length; i++) {
                sqlBuilder.append(":criticality").append(i);
                if (i < criticalityValues.length - 1) {
                    sqlBuilder.append(", ");
                }
                params.put("criticality" + i, criticalityValues[i].trim());
            }
            sqlBuilder.append(")");
        }

        // Add domain filter (matches derivedFrom pattern)
        if (domain != null && !domain.trim().isEmpty()) {
            sqlBuilder.append(" AND pf.derived_from = :derivedFrom");
            params.put("derivedFrom", domain.trim());
        }

        // Add field key filter
        if (fieldKey != null && !fieldKey.trim().isEmpty()) {
            sqlBuilder.append(" AND pf.field_key = :fieldKey");
            params.put("fieldKey", fieldKey.trim());
        }

        // Add text search filter (search in title, hypothesis, and field_key)
        if (search != null && !search.trim().isEmpty()) {
            sqlBuilder.append(" AND (rs.title ILIKE :search OR rs.hypothesis ILIKE :search OR rs.field_key ILIKE :search OR app.name ILIKE :search)");
            params.put("search", "%" + search.trim() + "%");
        }

        sqlBuilder.append(" ORDER BY rs.created_at DESC LIMIT :limit OFFSET :offset");
        params.put("limit", limit);
        params.put("offset", offset);

        return jdbc.query(sqlBuilder.toString(), params, this::mapRiskBlockedItem);
    }

    /**
     * Revoke evidence
     */
    public boolean revokeEvidence(String evidenceId, String reviewedBy) {
        String sql = """
            UPDATE evidence 
            SET status = 'revoked',
                revoked_at = now(),
                reviewed_by = :reviewedBy,
                reviewed_at = now(),
                updated_at = now()
            WHERE evidence_id = :evidenceId
            """;
        
        int updated = jdbc.update(sql, new MapSqlParameterSource()
            .addValue("evidenceId", evidenceId)
            .addValue("reviewedBy", reviewedBy)
        );
        
        log.info("Revoked evidence {} by {}", evidenceId, reviewedBy);
        return updated > 0;
    }
    
    /**
     * Map result set to Evidence
     */
    private Evidence mapEvidence(ResultSet rs, int rowNum) throws SQLException {
        return new Evidence(
            rs.getString("evidence_id"),
            rs.getString("app_id"),
            rs.getString("profile_field_id"),
            rs.getString("claim_id"),
            rs.getString("uri"),
            rs.getString("type"),
            rs.getString("sha256"),
            rs.getString("source_system"),
            rs.getString("submitted_by"),
            rs.getObject("valid_from", OffsetDateTime.class),
            rs.getObject("valid_until", OffsetDateTime.class),
            rs.getString("status"),
            rs.getObject("revoked_at", OffsetDateTime.class),
            rs.getString("reviewed_by"),
            rs.getObject("reviewed_at", OffsetDateTime.class),
            rs.getString("related_evidence_fields"),
            rs.getString("track_id"),
            rs.getString("document_id"),
            rs.getString("doc_version_id"),
            rs.getObject("added_at", OffsetDateTime.class),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class)
        );
    }
    

    private EnhancedEvidenceSummary mapEnhancedEvidenceSummary(ResultSet rs, int rowNum) throws SQLException {
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
            (Integer) rs.getObject("document_link_health")
        );
    }

    /**
     * Map result set to KpiEvidenceSummary with full application context
     */
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

        return switch (derivedFrom) {
            case "security_rating" -> rs.getString("security_rating");
            case "confidentiality_rating" -> rs.getString("confidentiality_rating");
            case "integrity_rating" -> rs.getString("integrity_rating");
            case "availability_rating" -> rs.getString("availability_rating");
            case "resilience_rating" -> rs.getString("resilience_rating");
            case "app_criticality_assessment" -> rs.getString("app_criticality_assessment");
            default -> null;
        };
    }

    /**
     * Map result set to RiskBlockedItem with full application context
     */
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

    /**
     * Find all documents attached as evidence to a specific profile field
     */
    public List<AttachedDocumentInfo> findAttachedDocuments(String appId, String profileFieldId) {
        String sql = """
            SELECT 
                e.evidence_id,
                e.document_id,
                d.title,
                d.canonical_url as url,
                e.created_at as attached_at,
                e.source_system,
                e.submitted_by
            FROM evidence e
            JOIN document d ON e.document_id = d.document_id
            WHERE e.app_id = ? 
              AND e.profile_field_id = ?
              AND e.document_id IS NOT NULL
              AND e.revoked_at IS NULL
            ORDER BY e.created_at DESC
            """;
        
        return jdbc.getJdbcTemplate().query(sql, this::mapAttachedDocumentInfo, appId, profileFieldId);
    }

    /**
     * Find enhanced attached documents with full document metadata
     */
    public List<Map<String, Object>> findEnhancedAttachedDocuments(String appId, String profileFieldId) {
        String sql = """
            SELECT 
                d.document_id,
                d.app_id,
                d.title,
                d.canonical_url,
                d.source_type,
                d.owners,
                d.link_health,
                ARRAY_AGG(DISTINCT dt.field_key) as related_evidence_fields,
                d.created_at as doc_created_at,
                d.updated_at as doc_updated_at,
                e.evidence_id,
                e.created_at as attached_at,
                e.source_system,
                e.submitted_by,
                dv.doc_version_id,
                dv.version_id,
                dv.url_at_version,
                dv.author,
                dv.source_date as version_source_date,
                dv.created_at as version_created_at
            FROM evidence e
            JOIN document d ON e.document_id = d.document_id
            LEFT JOIN document_related_evidence_field dt ON d.document_id = dt.document_id
            LEFT JOIN document_version dv ON d.document_id = dv.document_id 
                AND dv.doc_version_id = (
                    SELECT doc_version_id 
                    FROM document_version dv2 
                    WHERE dv2.document_id = d.document_id 
                    ORDER BY dv2.source_date DESC 
                    LIMIT 1
                )
            WHERE e.app_id = ? 
              AND e.profile_field_id = ?
              AND e.document_id IS NOT NULL
              AND e.revoked_at IS NULL
            GROUP BY d.document_id, d.app_id, d.title, d.canonical_url, d.source_type, d.owners,
                     d.link_health, d.created_at, d.updated_at,
                     e.evidence_id, e.created_at, e.source_system, e.submitted_by,
                     dv.doc_version_id, dv.version_id, dv.url_at_version, dv.author, dv.source_date, dv.created_at
            ORDER BY e.created_at DESC
            """;
        
        return jdbc.getJdbcTemplate().queryForList(sql, appId, profileFieldId);
    }

    /**
     * Count compliant evidence records
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
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        // Add profile version filtering (KPI-style logic)
        if (appId != null && !appId.trim().isEmpty()) {
            sqlBuilder.append(" AND p.app_id = :appId");
            sqlBuilder.append(" AND p.version = (SELECT MAX(version) FROM profile WHERE app_id = :appId)");
            params.put("appId", appId.trim());
        } else {
            // For portfolio-wide searches, ensure we only get latest versions for each app
            sqlBuilder.append(" AND p.version = (SELECT MAX(version) FROM profile p2 WHERE p2.app_id = p.app_id)");
        }

        // Add same filter logic as find method
        if (criticality != null && !criticality.trim().isEmpty()) {
            String[] criticalityValues = criticality.split(",");
            sqlBuilder.append(" AND app.app_criticality_assessment IN (");
            for (int i = 0; i < criticalityValues.length; i++) {
                sqlBuilder.append(":criticality").append(i);
                if (i < criticalityValues.length - 1) {
                    sqlBuilder.append(", ");
                }
                params.put("criticality" + i, criticalityValues[i].trim());
            }
            sqlBuilder.append(")");
        }

        if (domain != null && !domain.trim().isEmpty()) {
            sqlBuilder.append(" AND pf.derived_from = :derivedFrom");
            params.put("derivedFrom", domain.trim());
        }

        if (fieldKey != null && !fieldKey.trim().isEmpty()) {
            sqlBuilder.append(" AND pf.field_key = :fieldKey");
            params.put("fieldKey", fieldKey.trim());
        }

        if (search != null && !search.trim().isEmpty()) {
            sqlBuilder.append(" AND (e.uri ILIKE :search OR d.title ILIKE :search OR pf.field_key ILIKE :search)");
            params.put("search", "%" + search.trim() + "%");
        }

        return jdbc.queryForObject(sqlBuilder.toString(), params, Long.class);
    }

    /**
     * Count pending review evidence records
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

        java.util.Map<String, Object> params = new java.util.HashMap<>();

        // Add profile version filtering (KPI-style logic)
        if (appId != null && !appId.trim().isEmpty()) {
            sqlBuilder.append(" AND p.app_id = :appId");
            sqlBuilder.append(" AND p.version = (SELECT MAX(version) FROM profile WHERE app_id = :appId)");
            params.put("appId", appId.trim());
        } else {
            // For portfolio-wide searches, ensure we only get latest versions for each app
            sqlBuilder.append(" AND p.version = (SELECT MAX(version) FROM profile p2 WHERE p2.app_id = p.app_id)");
        }

        // Add criticality filter
        if (criticality != null && !criticality.trim().isEmpty()) {
            String[] criticalityValues = criticality.split(",");
            sqlBuilder.append(" AND app.app_criticality_assessment IN (");
            for (int i = 0; i < criticalityValues.length; i++) {
                sqlBuilder.append(":criticality").append(i);
                if (i < criticalityValues.length - 1) {
                    sqlBuilder.append(", ");
                }
                params.put("criticality" + i, criticalityValues[i].trim());
            }
            sqlBuilder.append(")");
        }

        // Add domain filter (matches derivedFrom pattern)
        if (domain != null && !domain.trim().isEmpty()) {
            sqlBuilder.append(" AND pf.derived_from = :derivedFrom");
            params.put("derivedFrom", domain.trim());
        }

        // Add field key filter
        if (fieldKey != null && !fieldKey.trim().isEmpty()) {
            sqlBuilder.append(" AND pf.field_key = :fieldKey");
            params.put("fieldKey", fieldKey.trim());
        }

        // Add text search filter
        if (search != null && !search.trim().isEmpty()) {
            sqlBuilder.append(" AND (e.uri ILIKE :search OR d.title ILIKE :search OR pf.field_key ILIKE :search)");
            params.put("search", "%" + search.trim() + "%");
        }

        return jdbc.queryForObject(sqlBuilder.toString(), params, Long.class);
    }

    /**
     * Count missing evidence profile fields
     */
    public long countMissingEvidenceFields(String appId, String criticality, String domain, String fieldKey, String search) {
        StringBuilder sqlBuilder = new StringBuilder("""
            SELECT COUNT(DISTINCT pf.id)
            FROM profile p
            JOIN profile_field pf ON p.profile_id = pf.profile_id
            LEFT JOIN application app ON p.app_id = app.app_id
            WHERE NOT EXISTS (
                SELECT 1 FROM evidence e
                WHERE e.profile_field_id = pf.id
            )
            """);

        java.util.Map<String, Object> params = new java.util.HashMap<>();

        // Add profile version filtering (KPI-style logic)
        if (appId != null && !appId.trim().isEmpty()) {
            sqlBuilder.append(" AND p.app_id = :appId");
            sqlBuilder.append(" AND p.version = (SELECT MAX(version) FROM profile WHERE app_id = :appId)");
            params.put("appId", appId.trim());
        } else {
            // For portfolio-wide searches, ensure we only get latest versions for each app
            sqlBuilder.append(" AND p.version = (SELECT MAX(version) FROM profile p2 WHERE p2.app_id = p.app_id)");
        }

        // Add criticality filter
        if (criticality != null && !criticality.trim().isEmpty()) {
            String[] criticalityValues = criticality.split(",");
            sqlBuilder.append(" AND app.app_criticality_assessment IN (");
            for (int i = 0; i < criticalityValues.length; i++) {
                sqlBuilder.append(":criticality").append(i);
                if (i < criticalityValues.length - 1) {
                    sqlBuilder.append(", ");
                }
                params.put("criticality" + i, criticalityValues[i].trim());
            }
            sqlBuilder.append(")");
        }

        // Add domain filter (matches derivedFrom pattern)
        if (domain != null && !domain.trim().isEmpty()) {
            sqlBuilder.append(" AND pf.derived_from = :derivedFrom");
            params.put("derivedFrom", domain.trim());
        }

        // Add field key filter
        if (fieldKey != null && !fieldKey.trim().isEmpty()) {
            sqlBuilder.append(" AND pf.field_key = :fieldKey");
            params.put("fieldKey", fieldKey.trim());
        }

        // Add text search filter (for missing evidence, search only field_key and app_name)
        if (search != null && !search.trim().isEmpty()) {
            sqlBuilder.append(" AND (pf.field_key ILIKE :search OR app.name ILIKE :search)");
            params.put("search", "%" + search.trim() + "%");
        }

        return jdbc.queryForObject(sqlBuilder.toString(), params, Long.class);
    }

    /**
     * Count risk blocked items
     */
    public long countRiskBlockedItems(String appId, String criticality, String domain, String fieldKey, String search) {
        StringBuilder sqlBuilder = new StringBuilder("""
            SELECT COUNT(DISTINCT rs.risk_id)
            FROM risk_story rs
            LEFT JOIN application app ON rs.app_id = app.app_id
            LEFT JOIN profile p ON rs.app_id = p.app_id
                AND p.version = (SELECT MAX(version) FROM profile p2 WHERE p2.app_id = rs.app_id)
            LEFT JOIN profile_field pf ON rs.field_key = pf.field_key
                AND pf.profile_id = p.profile_id
            WHERE rs.status IN ('PENDING_SME_REVIEW', 'UNDER_REVIEW', 'OPEN')
            """);

        java.util.Map<String, Object> params = new java.util.HashMap<>();

        // Add profile version filtering (KPI-style logic)
        if (appId != null && !appId.trim().isEmpty()) {
            sqlBuilder.append(" AND rs.app_id = :appId");
            sqlBuilder.append(" AND p.version = (SELECT MAX(version) FROM profile WHERE app_id = :appId)");
            params.put("appId", appId.trim());
        } else {
            // For portfolio-wide searches, ensure we only get latest versions for each app
            sqlBuilder.append(" AND p.version = (SELECT MAX(version) FROM profile p2 WHERE p2.app_id = p.app_id)");
        }

        // Add criticality filter
        if (criticality != null && !criticality.trim().isEmpty()) {
            String[] criticalityValues = criticality.split(",");
            sqlBuilder.append(" AND app.app_criticality_assessment IN (");
            for (int i = 0; i < criticalityValues.length; i++) {
                sqlBuilder.append(":criticality").append(i);
                if (i < criticalityValues.length - 1) {
                    sqlBuilder.append(", ");
                }
                params.put("criticality" + i, criticalityValues[i].trim());
            }
            sqlBuilder.append(")");
        }

        // Add domain filter (matches derivedFrom pattern)
        if (domain != null && !domain.trim().isEmpty()) {
            sqlBuilder.append(" AND pf.derived_from = :derivedFrom");
            params.put("derivedFrom", domain.trim());
        }

        // Add field key filter
        if (fieldKey != null && !fieldKey.trim().isEmpty()) {
            sqlBuilder.append(" AND pf.field_key = :fieldKey");
            params.put("fieldKey", fieldKey.trim());
        }

        // Add text search filter (search in title, hypothesis, and field_key)
        if (search != null && !search.trim().isEmpty()) {
            sqlBuilder.append(" AND (rs.title ILIKE :search OR rs.hypothesis ILIKE :search OR rs.field_key ILIKE :search OR app.name ILIKE :search)");
            params.put("search", "%" + search.trim() + "%");
        }

        return jdbc.queryForObject(sqlBuilder.toString(), params, Long.class);
    }

    /**
     * Delete evidence records by app, profile field, and document ID
     */
    public int deleteByAppIdProfileFieldIdAndDocumentId(String appId, String profileFieldId, String documentId) {
        String sql = """
            DELETE FROM evidence 
            WHERE app_id = ? 
              AND profile_field_id = ? 
              AND document_id = ?
            """;
        
        return jdbc.getJdbcTemplate().update(sql, appId, profileFieldId, documentId);
    }

    private AttachedDocumentInfo mapAttachedDocumentInfo(ResultSet rs, int rowNum) throws SQLException {
        return new AttachedDocumentInfo(
            rs.getString("document_id"),
            rs.getString("evidence_id"),
            rs.getString("title"),
            rs.getString("url"),
            rs.getObject("attached_at", OffsetDateTime.class),
            rs.getString("source_system"),
            rs.getString("submitted_by")
        );
    }
}