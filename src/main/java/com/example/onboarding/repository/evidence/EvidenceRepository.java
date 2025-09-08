package com.example.onboarding.repository.evidence;

import com.example.onboarding.dto.evidence.Evidence;
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
import java.time.ZoneOffset;
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
     */
    public List<com.example.onboarding.dto.evidence.EnhancedEvidenceSummary> findEvidenceByApp(String appId, int limit, int offset) {
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
            LEFT JOIN document d ON e.document_id = d.document_id
            WHERE e.app_id = :appId
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
     */
    public List<com.example.onboarding.dto.evidence.EnhancedEvidenceSummary> findEvidenceByProfileField(String profileFieldId, int limit, int offset) {
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
            LEFT JOIN document d ON e.document_id = d.document_id
            WHERE e.profile_field_id = :profileFieldId
            ORDER BY e.created_at DESC
            LIMIT :limit OFFSET :offset
            """;
        
        return jdbc.query(sql, Map.of("profileFieldId", profileFieldId, "limit", limit, "offset", offset),
            this::mapEnhancedEvidenceSummary);
    }
    
    
    /**
     * Find enhanced evidence by claim with EvidenceFieldLink metadata and document source info
     */
    public List<com.example.onboarding.dto.evidence.EnhancedEvidenceSummary> findEvidenceByClaim(String claimId, int limit, int offset) {
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
            LEFT JOIN document d ON e.document_id = d.document_id
            WHERE e.claim_id = :claimId
            ORDER BY e.created_at DESC
            LIMIT :limit OFFSET :offset
            """;
        
        return jdbc.query(sql, Map.of("claimId", claimId, "limit", limit, "offset", offset),
            this::mapEnhancedEvidenceSummary);
    }
    
    /**
     * Find enhanced evidence by track with EvidenceFieldLink metadata and document source info
     */
    public List<com.example.onboarding.dto.evidence.EnhancedEvidenceSummary> findEvidenceByTrack(String trackId, int limit, int offset) {
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
            LEFT JOIN document d ON e.document_id = d.document_id
            WHERE e.track_id = :trackId
            ORDER BY e.created_at DESC
            LIMIT :limit OFFSET :offset
            """;
        
        return jdbc.query(sql, Map.of("trackId", trackId, "limit", limit, "offset", offset),
            this::mapEnhancedEvidenceSummary);
    }
    
    /**
     * Search evidence with multiple filters
     */
    public List<com.example.onboarding.dto.evidence.EnhancedEvidenceSummary> searchEvidence(
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
            LEFT JOIN document d ON e.document_id = d.document_id
            LEFT JOIN profile_field pf ON e.profile_field_id = pf.id
            LEFT JOIN application app ON e.app_id = app.app_id
            WHERE 1=1
            """);
        
        java.util.Map<String, Object> params = new java.util.HashMap<>();
        
        // Add dynamic filters
        if (linkStatus != null && !linkStatus.trim().isEmpty()) {
            sqlBuilder.append(" AND efl.link_status = :linkStatus");
            params.put("linkStatus", linkStatus.trim());
        }
        
        if (appId != null && !appId.trim().isEmpty()) {
            sqlBuilder.append(" AND e.app_id = :appId");
            params.put("appId", appId.trim());
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
            // For SME assignment, we'd need to join with risk_story table
            sqlBuilder.append(" AND EXISTS (SELECT 1 FROM risk_story rs WHERE rs.field_key = pf.field_key AND rs.app_id = e.app_id AND rs.assigned_sme = :assignedSme)");
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
    

    private com.example.onboarding.dto.evidence.EnhancedEvidenceSummary mapEnhancedEvidenceSummary(ResultSet rs, int rowNum) throws SQLException {
        return new com.example.onboarding.dto.evidence.EnhancedEvidenceSummary(
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
            com.example.onboarding.model.EvidenceFieldLinkStatus.valueOf(rs.getString("link_status")),
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
     * Find all documents attached as evidence to a specific profile field
     */
    public List<com.example.onboarding.dto.evidence.AttachedDocumentInfo> findAttachedDocuments(String appId, String profileFieldId) {
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

    private com.example.onboarding.dto.evidence.AttachedDocumentInfo mapAttachedDocumentInfo(ResultSet rs, int rowNum) throws SQLException {
        return new com.example.onboarding.dto.evidence.AttachedDocumentInfo(
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