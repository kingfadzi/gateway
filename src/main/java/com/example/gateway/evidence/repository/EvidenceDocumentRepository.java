package com.example.gateway.evidence.repository;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * Repository for evidence document-related queries.
 * Handles document attachment and retrieval operations for evidence.
 */
@Repository
public class EvidenceDocumentRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public EvidenceDocumentRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Find enhanced attached documents for a profile field
     * Includes document metadata, version info, and attachment details
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
}
