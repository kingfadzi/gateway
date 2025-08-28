package com.example.onboarding.repository.document;

import com.example.onboarding.dto.document.DocumentResponse;
import com.example.onboarding.dto.document.DocumentSummary;
import com.example.onboarding.dto.document.DocumentVersionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DocumentRepository {
    
    private static final Logger log = LoggerFactory.getLogger(DocumentRepository.class);
    
    private final NamedParameterJdbcTemplate jdbc;
    
    public DocumentRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }
    
    /**
     * Create a new document
     */
    public String createDocument(String appId, String title, String canonicalUrl, String sourceType, String owners, Integer linkHealth) {
        String documentId = "doc_" + UUID.randomUUID().toString().replace("-", "");
        
        String sql = """
            INSERT INTO document (document_id, app_id, title, canonical_url, source_type, owners, link_health, created_at, updated_at)
            VALUES (:docId, :appId, :title, :url, :sourceType, :owners, :health, now(), now())
            """;
        
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("docId", documentId)
                .addValue("appId", appId)
                .addValue("title", title)
                .addValue("url", canonicalUrl)
                .addValue("sourceType", sourceType)
                .addValue("owners", owners)
                .addValue("health", linkHealth));
        
        return documentId;
    }
    
    /**
     * Create a document version
     */
    /**
     * Check if a version already exists for this document
     */
    public boolean versionExists(String documentId, String versionId) {
        String sql = """
            SELECT COUNT(*) FROM document_version 
            WHERE document_id = :docId AND version_id = :versionId
            """;
        
        Integer count = jdbc.queryForObject(sql, 
                Map.of("docId", documentId, "versionId", versionId), 
                Integer.class);
        
        return count != null && count > 0;
    }
    
    /**
     * Find existing document by URL in the app
     */
    public Optional<DocumentResponse> findDocumentByUrl(String appId, String canonicalUrl) {
        // First get the document (should be unique by app_id + canonical_url)
        String docSql = """
            SELECT * FROM document 
            WHERE app_id = :appId AND canonical_url = :url
            LIMIT 1
            """;
        
        try {
            DocumentResponse doc = jdbc.queryForObject(docSql, Map.of("appId", appId, "url", canonicalUrl), (rs, rowNum) -> {
                String documentId = rs.getString("document_id");
                
                // Get related evidence fields for this document
                String tagSql = "SELECT field_key FROM document_related_evidence_field WHERE document_id = :docId";
                List<String> relatedEvidenceFields = jdbc.queryForList(tagSql, Map.of("docId", documentId), String.class);
                
                // Get latest version for this document
                String versionSql = """
                    SELECT * FROM document_version 
                    WHERE document_id = :docId 
                    ORDER BY created_at DESC 
                    LIMIT 1
                    """;
                
                DocumentVersionInfo versionInfo = null;
                try {
                    versionInfo = jdbc.queryForObject(versionSql, Map.of("docId", documentId), (versionRs, versionRowNum) -> 
                        new DocumentVersionInfo(
                            versionRs.getString("doc_version_id"),
                            versionRs.getString("version_id"),
                            versionRs.getString("url_at_version"),
                            versionRs.getString("author"),
                            versionRs.getObject("source_date", OffsetDateTime.class),
                            versionRs.getObject("created_at", OffsetDateTime.class)
                        )
                    );
                } catch (EmptyResultDataAccessException e) {
                    // No version found, which is fine
                }
                
                return new DocumentResponse(
                        documentId,
                        rs.getString("app_id"),
                        rs.getString("title"),
                        rs.getString("canonical_url"),
                        rs.getString("source_type"),
                        rs.getString("owners"),
                        (Integer) rs.getObject("link_health"),
                        relatedEvidenceFields,
                        versionInfo,
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getObject("updated_at", OffsetDateTime.class)
                );
            });
            
            return Optional.of(doc);
            
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
    
    public String createDocumentVersion(String documentId, String versionId, String urlAtVersion, String author, OffsetDateTime sourceDate) {
        // Check if version already exists
        if (versionExists(documentId, versionId)) {
            log.debug("Version {} already exists for document {}, skipping creation", versionId, documentId);
            // Return the existing version ID
            String sql = """
                SELECT doc_version_id FROM document_version 
                WHERE document_id = :docId AND version_id = :versionId 
                ORDER BY created_at DESC 
                LIMIT 1
                """;
            return jdbc.queryForObject(sql, 
                    Map.of("docId", documentId, "versionId", versionId), 
                    String.class);
        }
        
        String docVersionId = "dv_" + UUID.randomUUID().toString().replace("-", "");
        
        String sql = """
            INSERT INTO document_version (doc_version_id, document_id, version_id, url_at_version, author, source_date, created_at)
            VALUES (:dvId, :docId, :versionId, :urlAtVersion, :author, :sourceDate, now())
            """;
        
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("dvId", docVersionId)
                .addValue("docId", documentId)
                .addValue("versionId", versionId)
                .addValue("urlAtVersion", urlAtVersion)
                .addValue("author", author)
                .addValue("sourceDate", sourceDate));
        
        log.debug("Created new version record {} for document {} (version: {})", docVersionId, documentId, versionId);
        return docVersionId;
    }
    
    /**
     * Add related evidence fields to a document
     */
    public void addDocumentTags(String documentId, List<String> fieldKeys) {
        if (fieldKeys == null || fieldKeys.isEmpty()) {
            return;
        }
        
        // First, remove existing related evidence fields
        jdbc.update("DELETE FROM document_related_evidence_field WHERE document_id = :docId", 
                   Map.of("docId", documentId));
        
        // Insert new related evidence fields
        String sql = "INSERT INTO document_related_evidence_field (document_id, field_key) VALUES (:docId, :fieldKey)";
        
        for (String fieldKey : fieldKeys) {
            jdbc.update(sql, new MapSqlParameterSource()
                    .addValue("docId", documentId)
                    .addValue("fieldKey", fieldKey));
        }
    }
    
    /**
     * Get documents for an application with pagination
     */
    public List<DocumentSummary> getDocumentsByApp(String appId, int limit, int offset) {
        String sql = """
            SELECT d.*,
                   dv.doc_version_id, dv.version_id, dv.url_at_version, dv.author, dv.source_date, 
                   dv.created_at as version_created_at,
                   ARRAY_AGG(DISTINCT dt.field_key) as related_evidence_fields
            FROM document d
            LEFT JOIN document_version dv ON d.document_id = dv.document_id
                                         AND dv.created_at = (SELECT MAX(created_at) 
                                                             FROM document_version 
                                                             WHERE document_id = d.document_id)
            LEFT JOIN document_related_evidence_field dt ON d.document_id = dt.document_id
            WHERE d.app_id = :appId
            GROUP BY d.document_id, d.app_id, d.title, d.canonical_url, d.source_type, d.owners,
                     d.link_health, d.created_at, d.updated_at,
                     dv.doc_version_id, dv.version_id, dv.url_at_version, dv.author, dv.source_date, dv.created_at
            ORDER BY d.updated_at DESC
            LIMIT :limit OFFSET :offset
            """;
        
        return jdbc.query(sql, Map.of("appId", appId, "limit", limit, "offset", offset), (rs, rowNum) -> {
            @SuppressWarnings("unchecked")
            String[] relatedFields = (String[]) rs.getArray("related_evidence_fields").getArray();
            List<String> relatedEvidenceFields = relatedFields != null ? List.of(relatedFields) : List.of();
            
            DocumentVersionInfo versionInfo = null;
            String docVersionId = rs.getString("doc_version_id");
            if (docVersionId != null) {
                versionInfo = new DocumentVersionInfo(
                        docVersionId,
                        rs.getString("version_id"),
                        rs.getString("url_at_version"),
                        rs.getString("author"),
                        rs.getObject("source_date", OffsetDateTime.class),
                        rs.getObject("version_created_at", OffsetDateTime.class)
                );
            }
            
            return new DocumentSummary(
                    rs.getString("document_id"),
                    rs.getString("app_id"),
                    rs.getString("title"),
                    rs.getString("canonical_url"),
                    rs.getString("source_type"),
                    rs.getString("owners"),
                    (Integer) rs.getObject("link_health"),
                    relatedEvidenceFields,
                    versionInfo,
                    rs.getObject("created_at", OffsetDateTime.class),
                    rs.getObject("updated_at", OffsetDateTime.class)
            );
        });
    }
    
    /**
     * Count total documents for an application
     */
    public long countDocumentsByApp(String appId) {
        String sql = """
            SELECT COUNT(DISTINCT d.document_id) 
            FROM document d 
            WHERE d.app_id = :appId
            """;
        
        Integer count = jdbc.queryForObject(sql, Map.of("appId", appId), Integer.class);
        return count != null ? count : 0;
    }
    
    /**
     * Get documents by field type (for evidence selection)
     */
    public List<DocumentSummary> getDocumentsByFieldType(String appId, String fieldKey) {
        String sql = """
            SELECT d.*,
                   dv.doc_version_id, dv.version_id, dv.url_at_version, dv.author, dv.source_date, 
                   dv.created_at as version_created_at,
                   ARRAY_AGG(DISTINCT dt.field_key) as related_evidence_fields
            FROM document d
            JOIN document_related_evidence_field dt ON d.document_id = dt.document_id AND dt.field_key = :fieldKey
            LEFT JOIN document_version dv ON d.document_id = dv.document_id
                                         AND dv.created_at = (SELECT MAX(created_at) 
                                                             FROM document_version 
                                                             WHERE document_id = d.document_id)
            WHERE d.app_id = :appId
            GROUP BY d.document_id, d.app_id, d.title, d.canonical_url, d.source_type, d.owners,
                     d.link_health, d.created_at, d.updated_at,
                     dv.doc_version_id, dv.version_id, dv.url_at_version, dv.author, dv.source_date, dv.created_at
            ORDER BY d.updated_at DESC
            """;
        
        return jdbc.query(sql, new MapSqlParameterSource()
                .addValue("appId", appId)
                .addValue("fieldKey", fieldKey), (rs, rowNum) -> {
            @SuppressWarnings("unchecked")
            String[] relatedFields = (String[]) rs.getArray("related_evidence_fields").getArray();
            List<String> relatedEvidenceFields = relatedFields != null ? List.of(relatedFields) : List.of();
            
            DocumentVersionInfo versionInfo = null;
            String docVersionId = rs.getString("doc_version_id");
            if (docVersionId != null) {
                versionInfo = new DocumentVersionInfo(
                        docVersionId,
                        rs.getString("version_id"),
                        rs.getString("url_at_version"),
                        rs.getString("author"),
                        rs.getObject("source_date", OffsetDateTime.class),
                        rs.getObject("version_created_at", OffsetDateTime.class)
                );
            }
            
            return new DocumentSummary(
                    rs.getString("document_id"),
                    rs.getString("app_id"),
                    rs.getString("title"),
                    rs.getString("canonical_url"),
                    rs.getString("source_type"),
                    rs.getString("owners"),
                    (Integer) rs.getObject("link_health"),
                    relatedEvidenceFields,
                    versionInfo,
                    rs.getObject("created_at", OffsetDateTime.class),
                    rs.getObject("updated_at", OffsetDateTime.class)
            );
        });
    }
    
    /**
     * Get full document details
     */
    public Optional<DocumentResponse> getDocumentById(String documentId) {
        String sql = """
            SELECT d.*, 
                   ARRAY_AGG(DISTINCT dt.field_key) as related_evidence_fields,
                   dv.doc_version_id, dv.version_id, dv.url_at_version, dv.author, dv.source_date, dv.created_at as version_created_at
            FROM document d
            LEFT JOIN document_related_evidence_field dt ON d.document_id = dt.document_id
            LEFT JOIN document_version dv ON d.document_id = dv.document_id
                                         AND dv.created_at = (SELECT MAX(created_at) 
                                                             FROM document_version 
                                                             WHERE document_id = d.document_id)
            WHERE d.document_id = :docId
            GROUP BY d.document_id, d.app_id, d.title, d.canonical_url, d.source_type, d.owners, 
                     d.link_health, d.created_at, d.updated_at,
                     dv.doc_version_id, dv.version_id, dv.url_at_version, dv.author, dv.source_date, dv.created_at
            """;
        
        try {
            return Optional.of(jdbc.queryForObject(sql, Map.of("docId", documentId), (rs, rowNum) -> {
                @SuppressWarnings("unchecked")
                String[] relatedFields = (String[]) rs.getArray("related_evidence_fields").getArray();
                List<String> relatedEvidenceFields = relatedFields != null ? List.of(relatedFields) : List.of();
                
                DocumentVersionInfo versionInfo = null;
                String docVersionId = rs.getString("doc_version_id");
                if (docVersionId != null) {
                    versionInfo = new DocumentVersionInfo(
                            docVersionId,
                            rs.getString("version_id"),
                            rs.getString("url_at_version"),
                            rs.getString("author"),
                            rs.getObject("source_date", OffsetDateTime.class),
                            rs.getObject("version_created_at", OffsetDateTime.class)
                    );
                }
                
                return new DocumentResponse(
                        rs.getString("document_id"),
                        rs.getString("app_id"),
                        rs.getString("title"),
                        rs.getString("canonical_url"),
                        rs.getString("source_type"),
                        rs.getString("owners"),
                        (Integer) rs.getObject("link_health"),
                        relatedEvidenceFields,
                        versionInfo,
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getObject("updated_at", OffsetDateTime.class)
                );
            }));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
    
    /**
     * Update document health status
     */
    public void updateDocumentHealth(String documentId, Integer linkHealth) {
        jdbc.update("UPDATE document SET link_health = :health, updated_at = now() WHERE document_id = :docId",
                   new MapSqlParameterSource()
                           .addValue("health", linkHealth)
                           .addValue("docId", documentId));
    }
    
    /**
     * Update document owners
     */
    public void updateDocumentOwners(String documentId, String owners) {
        jdbc.update("UPDATE document SET owners = :owners, updated_at = now() WHERE document_id = :docId",
                   new MapSqlParameterSource()
                           .addValue("owners", owners)
                           .addValue("docId", documentId));
    }
}