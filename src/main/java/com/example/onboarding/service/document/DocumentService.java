package com.example.onboarding.service.document;

import com.example.onboarding.dto.PageResponse;
import com.example.onboarding.dto.document.CreateDocumentRequest;
import com.example.onboarding.dto.document.DocumentResponse;
import com.example.onboarding.dto.document.DocumentSummary;
import com.example.onboarding.dto.document.DocumentVersionInfo;
import com.example.onboarding.dto.document.EnhancedDocumentResponse;
import com.example.onboarding.repository.document.DocumentRepository;
import com.example.onboarding.service.profile.ProfileFieldRegistryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DocumentService {
    
    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    
    private final DocumentRepository documentRepository;
    private final ProfileFieldRegistryService profileFieldRegistryService;
    private final PlatformMetadataService platformMetadataService;
    private final PlatformDetectionService platformDetectionService;
    private final UrlValidationService urlValidationService;
    
    public DocumentService(DocumentRepository documentRepository, 
                          ProfileFieldRegistryService profileFieldRegistryService,
                          PlatformMetadataService platformMetadataService,
                          PlatformDetectionService platformDetectionService,
                          UrlValidationService urlValidationService) {
        this.documentRepository = documentRepository;
        this.profileFieldRegistryService = profileFieldRegistryService;
        this.platformMetadataService = platformMetadataService;
        this.platformDetectionService = platformDetectionService;
        this.urlValidationService = urlValidationService;
    }
    
    /**
     * Create a new document with async metadata extraction
     */
    @Transactional
    public DocumentResponse createDocument(String appId, CreateDocumentRequest request) {
        // Validate inputs
        if (request.title() == null || request.title().trim().isEmpty()) {
            throw new IllegalArgumentException("Document title is required");
        }
        
        if (request.url() == null || request.url().trim().isEmpty()) {
            throw new IllegalArgumentException("Document URL is required");
        }
        
        // Strict URL validation - reject fake/example domains
        ValidationResult urlValidation = urlValidationService.validateUrl(request.url());
        if (!urlValidation.isValid()) {
            throw new IllegalArgumentException("Invalid URL: " + urlValidation.getErrorMessage());
        }
        
        // Validate URL format and canonicalize
        String canonicalUrl = validateAndCanonicalizeUrl(request.url());
        
        // Validate field types against registry
        List<String> validFieldTypes = profileFieldRegistryService.validateFieldKeys(
                request.fieldTypes() != null ? request.fieldTypes() : List.of()
        );
        
        // Detect platform type using enhanced detection service
        PlatformDetectionService.PlatformInfo platformInfo = platformDetectionService.detectPlatform(canonicalUrl);
        String sourceType = platformInfo.getPlatformType();
        
        // Validate URL using detection results
        if (!platformInfo.isValid()) {
            throw new IllegalArgumentException("Invalid or unsupported URL: " + platformInfo.getErrorMessage());
        }
        
        // Check if document already exists for this URL
        Optional<DocumentResponse> existingDoc = documentRepository.findDocumentByUrl(appId, canonicalUrl);
        
        if (existingDoc.isPresent()) {
            log.debug("Found existing document for URL: {}", canonicalUrl);
            return handleExistingDocument(existingDoc.get(), canonicalUrl, sourceType, validFieldTypes);
        } else {
            log.debug("Creating new document for URL: {}", canonicalUrl);
            return createNewDocument(appId, request.title(), canonicalUrl, sourceType, validFieldTypes);
        }
    }
    
    private DocumentResponse handleExistingDocument(DocumentResponse existingDoc, String canonicalUrl, 
                                                  String sourceType, List<String> validFieldTypes) {
        try {
            // Extract current metadata from server to get latest commit hash
            PlatformMetadata currentMetadata = platformMetadataService.extractMetadata(canonicalUrl, sourceType);
            
            String currentVersionId = currentMetadata.getVersionId();
            String existingVersionId = existingDoc.latestVersion() != null ? existingDoc.latestVersion().versionId() : null;
            
            // Compare version IDs (commit hashes)
            if (currentVersionId != null && !currentVersionId.equals(existingVersionId)) {
                log.info("New version detected for document {}: {} -> {}", 
                        existingDoc.documentId(), existingVersionId, currentVersionId);
                
                // Create new version
                updateDocumentWithNewVersion(existingDoc.documentId(), currentMetadata, validFieldTypes);
                
                // Return updated document
                return documentRepository.getDocumentById(existingDoc.documentId())
                        .orElseThrow(() -> new RuntimeException("Failed to retrieve updated document"));
            } else {
                log.debug("No new version for document {}, returning existing", existingDoc.documentId());
                // Update related evidence fields if needed
                documentRepository.addDocumentTags(existingDoc.documentId(), validFieldTypes);
                return existingDoc;
            }
            
        } catch (Exception e) {
            log.error("Failed to check for new version of existing document {}: {}", 
                     existingDoc.documentId(), e.getMessage());
            throw new IllegalArgumentException("Failed to extract metadata from URL: " + e.getMessage(), e);
        }
    }
    
    private DocumentResponse createNewDocument(String appId, String title, String canonicalUrl, 
                                             String sourceType, List<String> validFieldTypes) {
        // Create document record
        String documentId = documentRepository.createDocument(
                appId, 
                title,
                canonicalUrl,
                sourceType,
                null, // owners will be set during metadata extraction
                null  // health will be set during metadata extraction
        );
        
        // Add related evidence fields for field types
        documentRepository.addDocumentTags(documentId, validFieldTypes);
        
        // Extract metadata synchronously to include version details in response
        try {
            extractMetadataSync(documentId, canonicalUrl, sourceType);
        } catch (Exception e) {
            // Clean up the created document if metadata extraction fails
            log.error("Metadata extraction failed for document {}, cleaning up: {}", documentId, e.getMessage());
            throw new IllegalArgumentException("Failed to extract metadata from URL: " + e.getMessage(), e);
        }
        
        // Return response with metadata populated
        return documentRepository.getDocumentById(documentId)
                .orElseThrow(() -> new RuntimeException("Failed to retrieve created document"));
    }
    
    private void updateDocumentWithNewVersion(String documentId, PlatformMetadata metadata, List<String> validFieldTypes) {
        // Update document with extracted metadata
        if (metadata.getOwners() != null) {
            documentRepository.updateDocumentOwners(documentId, metadata.getOwners());
            log.debug("Updated owners for document {}: {}", documentId, metadata.getOwners());
        }
        
        // Update health status
        if (metadata.getHealthStatus() != null) {
            documentRepository.updateDocumentHealth(documentId, metadata.getHealthStatus());
            log.debug("Updated health status for document {}: {}", documentId, metadata.getHealthStatus());
        }
        
        // Create new version record
        if (metadata.getVersionId() != null) {
            String versionId = documentRepository.createDocumentVersion(
                    documentId,
                    metadata.getVersionId(),
                    metadata.getVersionUrl(),
                    metadata.getAuthor(),
                    metadata.getSourceDate()
            );
            log.info("Created new version record {} for existing document {} (version: {})", 
                    versionId, documentId, metadata.getVersionId());
        }
        
        // Update related evidence fields if needed
        documentRepository.addDocumentTags(documentId, validFieldTypes);
        
        log.info("Successfully updated existing document {} with new version", documentId);
    }
    
    /**
     * Get documents for an application with pagination
     */
    public PageResponse<DocumentSummary> getDocumentsByApp(String appId, int page, int pageSize) {
        // Validate pagination parameters
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        int offset = (safePage - 1) * safePageSize;
        
        // Get total count and paginated results
        long total = documentRepository.countDocumentsByApp(appId);
        List<DocumentSummary> documents = documentRepository.getDocumentsByApp(appId, safePageSize, offset);
        
        return new PageResponse<>(safePage, safePageSize, total, documents);
    }
    
    /**
     * Get documents by field type (for evidence selection)
     */
    public List<DocumentSummary> getDocumentsByFieldType(String appId, String fieldKey) {
        return documentRepository.getDocumentsByFieldType(appId, fieldKey);
    }
    
    /**
     * Get document details by ID
     */
    public Optional<DocumentResponse> getDocumentById(String documentId) {
        return documentRepository.getDocumentById(documentId);
    }
    
    private String validateAndCanonicalizeUrl(String url) {
        try {
            URI uri = new URI(url.trim());
            if (uri.getScheme() == null || (!uri.getScheme().equals("http") && !uri.getScheme().equals("https"))) {
                throw new IllegalArgumentException("URL must use http or https protocol");
            }
            return uri.toString();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URL format: " + e.getMessage());
        }
    }
    
    // Platform detection is now handled by PlatformDetectionService
    
    private void extractMetadataSync(String documentId, String url, String sourceType) {
        try {
            log.debug("Starting metadata extraction for document {} ({})", documentId, sourceType);
            
            PlatformMetadata metadata = platformMetadataService.extractMetadata(url, sourceType);
            
            if (metadata != null) {
                // Update document with extracted metadata
                if (metadata.getOwners() != null) {
                    documentRepository.updateDocumentOwners(documentId, metadata.getOwners());
                    log.debug("Updated owners for document {}: {}", documentId, metadata.getOwners());
                }
                
                // Update health status
                if (metadata.getHealthStatus() != null) {
                    documentRepository.updateDocumentHealth(documentId, metadata.getHealthStatus());
                    log.debug("Updated health status for document {}: {}", documentId, metadata.getHealthStatus());
                }
                
                // Create version record if version info available
                if (metadata.getVersionId() != null) {
                    String versionId = documentRepository.createDocumentVersion(
                            documentId,
                            metadata.getVersionId(),
                            metadata.getVersionUrl(),
                            metadata.getAuthor(),
                            metadata.getSourceDate()
                    );
                    if (versionId != null) {
                        log.debug("Version record {} for document {} (version: {})", versionId, documentId, metadata.getVersionId());
                    }
                }
                
                log.info("Successfully completed metadata extraction for document {}", documentId);
            } else {
                log.warn("No metadata extracted for document {} from {}", documentId, url);
                documentRepository.updateDocumentHealth(documentId, 0); // Mark as unhealthy
            }
        } catch (Exception e) {
            log.error("Failed to extract metadata for document {} from {}: {}", documentId, url, e.getMessage());
            documentRepository.updateDocumentHealth(documentId, 0); // Mark as unhealthy
            // Re-throw the exception to propagate to caller
            throw e;
        }
    }

    /**
     * Get documents with attachment status for a specific profile field
     */
    public PageResponse<EnhancedDocumentResponse> getDocumentsWithAttachmentStatus(
            String appId, String profileFieldId, int page, int pageSize) {
        log.debug("Getting documents with attachment status for app {} and profile field {}", appId, profileFieldId);
        
        // Validate pagination parameters
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        int offset = (safePage - 1) * safePageSize;
        
        // Get total count and paginated results
        long total = documentRepository.countDocumentsForAttachmentStatus(appId);
        List<Map<String, Object>> results = documentRepository.findDocumentsWithAttachmentStatus(
            appId, profileFieldId, offset, safePageSize);
        
        List<EnhancedDocumentResponse> enhancedDocs = results.stream()
            .map(this::mapToEnhancedDocumentResponseFromDb)
            .collect(Collectors.toList());
        
        return new PageResponse<>(safePage, safePageSize, total, enhancedDocs);
    }

    /**
     * Map database result to EnhancedDocumentResponse
     */
    private EnhancedDocumentResponse mapToEnhancedDocumentResponseFromDb(Map<String, Object> row) {
        // Parse related_evidence_fields from PostgreSQL array
        List<String> relatedFields = parseRelatedEvidenceFieldsArray(row.get("related_evidence_fields"));
        
        // Create version info if available
        DocumentVersionInfo versionInfo = null;
        if (row.get("doc_version_id") != null) {
            versionInfo = new DocumentVersionInfo(
                (String) row.get("doc_version_id"),
                (String) row.get("version_id"),
                (String) row.get("url_at_version"),
                (String) row.get("author"),
                convertToOffsetDateTime(row.get("version_source_date")),
                convertToOffsetDateTime(row.get("version_created_at"))
            );
        }
        
        // Check if document is attached to the field
        boolean isAttached = Boolean.TRUE.equals(row.get("is_attached_to_field"));
        
        return new EnhancedDocumentResponse(
            (String) row.get("document_id"),
            (String) row.get("app_id"),
            (String) row.get("title"),
            (String) row.get("canonical_url"),
            (String) row.get("source_type"),
            (String) row.get("owners"),
            (Integer) row.get("link_health"),
            relatedFields,
            versionInfo,
            convertToOffsetDateTime(row.get("doc_created_at")),
            convertToOffsetDateTime(row.get("doc_updated_at")),
            isAttached,
            isAttached ? convertToOffsetDateTime(row.get("attached_at")) : null,
            isAttached ? (String) row.get("evidence_id") : null,
            isAttached ? (String) row.get("source_system") : null,
            isAttached ? (String) row.get("submitted_by") : null
        );
    }

    /**
     * Parse related evidence fields from PostgreSQL array
     */
    private List<String> parseRelatedEvidenceFieldsArray(Object arrayObj) {
        if (arrayObj == null) {
            return List.of();
        }
        try {
            if (arrayObj instanceof String[] stringArray) {
                return List.of(stringArray);
            } else if (arrayObj instanceof java.sql.Array sqlArray) {
                String[] stringArray = (String[]) sqlArray.getArray();
                return stringArray != null ? List.of(stringArray) : List.of();
            }
            return List.of();
        } catch (Exception e) {
            log.warn("Failed to parse related evidence fields array: {}", arrayObj, e);
            return List.of();
        }
    }

    /**
     * Parse related evidence fields JSON string to list (kept for backward compatibility)
     */
    private List<String> parseRelatedEvidenceFields(String jsonStr) {
        if (jsonStr == null || jsonStr.isBlank()) {
            return List.of();
        }
        try {
            // Simple JSON array parsing - could use ObjectMapper for more complex cases
            jsonStr = jsonStr.trim();
            if (jsonStr.startsWith("[") && jsonStr.endsWith("]")) {
                String content = jsonStr.substring(1, jsonStr.length() - 1);
                if (content.isBlank()) {
                    return List.of();
                }
                return Arrays.stream(content.split(","))
                    .map(s -> s.trim().replaceAll("^\"|\"$", ""))
                    .collect(Collectors.toList());
            }
            return List.of();
        } catch (Exception e) {
            log.warn("Failed to parse related evidence fields: {}", jsonStr, e);
            return List.of();
        }
    }

    /**
     * Convert database timestamp objects to OffsetDateTime
     */
    private OffsetDateTime convertToOffsetDateTime(Object timestampObj) {
        if (timestampObj == null) {
            return null;
        }
        if (timestampObj instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime;
        }
        if (timestampObj instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant().atOffset(java.time.ZoneOffset.UTC);
        }
        if (timestampObj instanceof java.time.LocalDateTime localDateTime) {
            return localDateTime.atOffset(java.time.ZoneOffset.UTC);
        }
        log.warn("Unexpected timestamp type: {}", timestampObj.getClass());
        return null;
    }
}