package com.example.onboarding.service.evidence;

import com.example.onboarding.dto.PageResponse;
import com.example.onboarding.dto.document.CreateDocumentRequest;
import com.example.onboarding.dto.document.DocumentResponse;
import com.example.onboarding.dto.document.DocumentVersionInfo;
import com.example.onboarding.dto.document.EnhancedDocumentResponse;
import com.example.onboarding.dto.evidence.AttachedDocumentInfo;
import com.example.onboarding.dto.evidence.AttachedDocumentsResponse;
import com.example.onboarding.dto.evidence.CreateEvidenceRequest;
import com.example.onboarding.dto.evidence.EnhancedAttachedDocumentsResponse;
import com.example.onboarding.dto.evidence.CreateEvidenceWithDocumentRequest;
import com.example.onboarding.dto.evidence.Evidence;
import com.example.onboarding.dto.evidence.EvidenceWithDocumentResponse;
import com.example.onboarding.dto.evidence.UpdateEvidenceRequest;
import com.example.onboarding.dto.evidence.AttachEvidenceToFieldRequest;
import com.example.onboarding.dto.evidence.EvidenceFieldLinkResponse;
import com.example.onboarding.dto.evidence.EvidenceUsageResponse;
import com.example.onboarding.repository.evidence.EvidenceRepository;
import com.example.onboarding.service.document.DocumentService;
import dev.controlplane.auditkit.annotations.Audited;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EvidenceServiceImpl implements EvidenceService {
    
    private static final Logger log = LoggerFactory.getLogger(EvidenceServiceImpl.class);
    
    private final EvidenceRepository evidenceRepository;
    private final DocumentService documentService;
    private final EvidenceFieldLinkService evidenceFieldLinkService;
    
    // Valid enum values for validation
    private static final Set<String> VALID_STATUSES = Set.of("active", "superseded", "revoked");
    
    public EvidenceServiceImpl(EvidenceRepository evidenceRepository, DocumentService documentService,
                               EvidenceFieldLinkService evidenceFieldLinkService) {
        this.evidenceRepository = evidenceRepository;
        this.documentService = documentService;
        this.evidenceFieldLinkService = evidenceFieldLinkService;
    }
    
    @Override
    @Transactional
    public Evidence createEvidence(String appId, CreateEvidenceRequest request) {
        log.debug("Creating evidence for app {}: {}", appId, request);
        
        // Validate required fields
        validateCreateRequest(appId, request);
        
        // Set defaults
        OffsetDateTime validFrom = request.validFrom() != null ? request.validFrom() : OffsetDateTime.now();
        
        try {
            String evidenceId = evidenceRepository.createEvidence(
                appId,
                request.profileFieldId(),
                request.uri(),
                request.type(),
                request.sourceSystem(),
                request.submittedBy(),
                validFrom,
                request.validUntil(),
                request.relatedEvidenceFields(),
                request.trackId(),
                request.documentId(),
                request.docVersionId()
            );
            
            log.info("Successfully created evidence {} for app {}", evidenceId, appId);
            
            return evidenceRepository.findEvidenceById(evidenceId)
                .orElseThrow(() -> new RuntimeException("Failed to retrieve created evidence"));
                
        } catch (Exception e) {
            log.error("Failed to create evidence for app {}: {}", appId, e.getMessage(), e);
            throw e;
        }
    }
    
    @Override
    @Transactional
    public Evidence updateEvidence(String evidenceId, UpdateEvidenceRequest request) {
        log.debug("Updating evidence {}: {}", evidenceId, request);
        
        // Validate evidence exists
        Evidence existing = evidenceRepository.findEvidenceById(evidenceId)
            .orElseThrow(() -> new IllegalArgumentException("Evidence not found: " + evidenceId));
        
        // Validate update request
        validateUpdateRequest(request);
        
        try {
            boolean updated = evidenceRepository.updateEvidence(
                evidenceId,
                request.uri(),
                request.type(),
                request.sourceSystem(),
                request.submittedBy(),
                request.validFrom(),
                request.validUntil(),
                request.status(),
                request.reviewedBy(),
                request.relatedEvidenceFields(),
                request.documentId(),
                request.docVersionId()
            );
            
            if (!updated) {
                throw new RuntimeException("Failed to update evidence: " + evidenceId);
            }
            
            log.info("Successfully updated evidence {}", evidenceId);
            
            return evidenceRepository.findEvidenceById(evidenceId)
                .orElseThrow(() -> new RuntimeException("Failed to retrieve updated evidence"));
                
        } catch (Exception e) {
            log.error("Failed to update evidence {}: {}", evidenceId, e.getMessage(), e);
            throw e;
        }
    }
    
    @Override
    public Optional<Evidence> getEvidenceById(String evidenceId) {
        return evidenceRepository.findEvidenceById(evidenceId);
    }
    
    @Override
    public PageResponse<com.example.onboarding.dto.evidence.EnhancedEvidenceSummary> getEvidenceByApp(String appId, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        int offset = (safePage - 1) * safePageSize;
        
        long total = evidenceRepository.countEvidenceByApp(appId);
        List<com.example.onboarding.dto.evidence.EnhancedEvidenceSummary> evidence = evidenceRepository.findEvidenceByApp(appId, safePageSize, offset);
        
        return new PageResponse<>(safePage, safePageSize, total, evidence);
    }
    
    @Override
    public PageResponse<com.example.onboarding.dto.evidence.EnhancedEvidenceSummary> getEvidenceByProfileField(String profileFieldId, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        int offset = (safePage - 1) * safePageSize;
        
        // Note: We don't have a count method for profile field, so we'll use the list size as approximation
        List<com.example.onboarding.dto.evidence.EnhancedEvidenceSummary> evidence = evidenceRepository.findEvidenceByProfileField(profileFieldId, safePageSize, offset);
        
        return new PageResponse<>(safePage, safePageSize, evidence.size(), evidence);
    }
    
    
    @Override
    public PageResponse<com.example.onboarding.dto.evidence.EnhancedEvidenceSummary> getEvidenceByClaim(String claimId, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        int offset = (safePage - 1) * safePageSize;
        
        List<com.example.onboarding.dto.evidence.EnhancedEvidenceSummary> evidence = evidenceRepository.findEvidenceByClaim(claimId, safePageSize, offset);
        
        return new PageResponse<>(safePage, safePageSize, evidence.size(), evidence);
    }
    
    @Override
    public PageResponse<com.example.onboarding.dto.evidence.EnhancedEvidenceSummary> getEvidenceByTrack(String trackId, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        int offset = (safePage - 1) * safePageSize;
        
        List<com.example.onboarding.dto.evidence.EnhancedEvidenceSummary> evidence = evidenceRepository.findEvidenceByTrack(trackId, safePageSize, offset);
        
        return new PageResponse<>(safePage, safePageSize, evidence.size(), evidence);
    }
    
    @Override
    @Transactional
    public Evidence revokeEvidence(String evidenceId, String reviewedBy) {
        log.debug("Revoking evidence {} by {}", evidenceId, reviewedBy);
        
        // Validate evidence exists
        Evidence existing = evidenceRepository.findEvidenceById(evidenceId)
            .orElseThrow(() -> new IllegalArgumentException("Evidence not found: " + evidenceId));
        
        if (reviewedBy == null || reviewedBy.trim().isEmpty()) {
            throw new IllegalArgumentException("ReviewedBy is required when revoking evidence");
        }
        
        try {
            boolean updated = evidenceRepository.revokeEvidence(evidenceId, reviewedBy);
            
            if (!updated) {
                throw new RuntimeException("Failed to revoke evidence: " + evidenceId);
            }
            
            log.info("Successfully revoked evidence {} by {}", evidenceId, reviewedBy);
            
            return evidenceRepository.findEvidenceById(evidenceId)
                .orElseThrow(() -> new RuntimeException("Failed to retrieve revoked evidence"));
                
        } catch (Exception e) {
            log.error("Failed to revoke evidence {}: {}", evidenceId, e.getMessage(), e);
            throw e;
        }
    }
    
    private void validateCreateRequest(String appId, CreateEvidenceRequest request) {
        if (appId == null || appId.trim().isEmpty()) {
            throw new IllegalArgumentException("App ID is required");
        }
        
        if (request.profileFieldId() == null || request.profileFieldId().trim().isEmpty()) {
            throw new IllegalArgumentException("Profile field ID is required");
        }
        
        if (request.uri() == null || request.uri().trim().isEmpty()) {
            throw new IllegalArgumentException("URI is required");
        }
    }
    
    @Override
    @Transactional
    @Audited(action = "CREATE_EVIDENCE_WITH_DOCUMENT", subjectType = "profile_field", subject = "#request.profileFieldId",
             context = {"appId=#appId", "evidenceId=#result.evidenceId", "documentUrl=#request.document.url"})
    public EvidenceWithDocumentResponse createEvidenceWithDocument(String appId, CreateEvidenceWithDocumentRequest request) {
        log.debug("Creating evidence with document for app {}: {}", appId, request);
        
        // Validate required fields
        validateCreateEvidenceWithDocumentRequest(appId, request);
        
        try {
            // Step 1: Create the document first
            CreateDocumentRequest documentRequest = new CreateDocumentRequest(
                request.document().title(),
                request.document().url(),
                request.document().relatedEvidenceFields()
            );
            
            DocumentResponse document = documentService.createDocument(appId, documentRequest);
            log.debug("Created document {} for evidence", document.documentId());
            
            // Step 2: Create evidence using the document
            var evidenceMeta = request.evidence();
            String docVersionId = (document.latestVersion() != null) ? document.latestVersion().docVersionId() : null;
            
            CreateEvidenceRequest evidenceRequest = new CreateEvidenceRequest(
                request.profileFieldId(),
                request.document().url(), // URI same as document URL
                evidenceMeta != null ? evidenceMeta.type() : "document",
                evidenceMeta != null ? evidenceMeta.sourceSystem() : "manual",
                evidenceMeta != null ? evidenceMeta.submittedBy() : null,
                evidenceMeta != null ? evidenceMeta.validFrom() : OffsetDateTime.now(),
                evidenceMeta != null ? evidenceMeta.validUntil() : null,
                evidenceMeta != null ? evidenceMeta.relatedEvidenceFields() : null,
                evidenceMeta != null ? evidenceMeta.trackId() : null,
                document.documentId(),
                docVersionId
            );
            
            Evidence evidence = createEvidence(appId, evidenceRequest);
            log.info("Successfully created evidence {} with document {} for app {}", 
                evidence.evidenceId(), document.documentId(), appId);
            
            // Step 3: Attach evidence to profile field to trigger auto-risk creation workflow
            log.debug("Attaching evidence {} to profile field {} for auto-risk evaluation", 
                evidence.evidenceId(), request.profileFieldId());
            
            AttachEvidenceToFieldRequest attachRequest = new AttachEvidenceToFieldRequest(
                "SYSTEM_EVIDENCE_WITH_DOCUMENT", 
                "Evidence created with document via frontend"
            );
            
            EvidenceFieldLinkResponse linkResponse = evidenceFieldLinkService.attachEvidenceToField(
                evidence.evidenceId(), 
                request.profileFieldId(), 
                appId, 
                attachRequest
            );
            
            log.info("Evidence {} attached to field {}. Risk created: {}, Risk ID: {}, Assigned SME: {}", 
                evidence.evidenceId(), request.profileFieldId(), 
                linkResponse.riskWasCreated(), linkResponse.autoCreatedRiskId(), linkResponse.assignedSme());
            
            // Step 4: Build response with both evidence, document, and auto-risk information
            return new EvidenceWithDocumentResponse(
                evidence.evidenceId(),
                evidence.appId(),
                evidence.profileFieldId(),
                evidence.claimId(),
                evidence.uri(),
                evidence.type(),
                evidence.sha256(),
                evidence.sourceSystem(),
                evidence.submittedBy(),
                evidence.validFrom(),
                evidence.validUntil(),
                evidence.status(),
                evidence.revokedAt(),
                evidence.reviewedBy(),
                evidence.reviewedAt(),
                evidence.relatedEvidenceFields(),
                evidence.trackId(),
                evidence.documentId(),
                evidence.docVersionId(),
                evidence.addedAt(),
                evidence.createdAt(),
                evidence.updatedAt(),
                document,
                // Auto-risk creation fields from evidence-field attachment workflow
                linkResponse.riskWasCreated(),
                linkResponse.autoCreatedRiskId(),
                linkResponse.assignedSme()
            );
            
        } catch (Exception e) {
            log.error("Failed to create evidence with document for app {}: {}", appId, e.getMessage(), e);
            throw e;
        }
    }
    
    private void validateCreateEvidenceWithDocumentRequest(String appId, CreateEvidenceWithDocumentRequest request) {
        if (appId == null || appId.trim().isEmpty()) {
            throw new IllegalArgumentException("App ID is required");
        }
        
        if (request.profileFieldId() == null || request.profileFieldId().trim().isEmpty()) {
            throw new IllegalArgumentException("Profile field ID is required");
        }
        
        if (request.document() == null) {
            throw new IllegalArgumentException("Document information is required");
        }
        
        var doc = request.document();
        if (doc.title() == null || doc.title().trim().isEmpty()) {
            throw new IllegalArgumentException("Document title is required");
        }
        
        if (doc.url() == null || doc.url().trim().isEmpty()) {
            throw new IllegalArgumentException("Document URL is required");
        }
    }
    
    private void validateUpdateRequest(UpdateEvidenceRequest request) {
        if (request.status() != null && !VALID_STATUSES.contains(request.status())) {
            throw new IllegalArgumentException("Invalid status. Valid values: " + VALID_STATUSES);
        }
    }

    @Override
    public AttachedDocumentsResponse getAttachedDocuments(String appId, String profileFieldId) {
        log.debug("Getting attached documents for profile field {} in app {}", profileFieldId, appId);
        
        // Get evidence linked to this profile field and that has document references
        List<AttachedDocumentInfo> attachedDocs = evidenceRepository.findAttachedDocuments(appId, profileFieldId);
        
        return new AttachedDocumentsResponse(profileFieldId, appId, attachedDocs);
    }

    @Override
    public EnhancedAttachedDocumentsResponse getEnhancedAttachedDocuments(String appId, String profileFieldId) {
        log.debug("Getting enhanced attached documents for profile field {} in app {}", profileFieldId, appId);
        
        List<Map<String, Object>> results = evidenceRepository.findEnhancedAttachedDocuments(appId, profileFieldId);
        
        List<EnhancedDocumentResponse> enhancedDocs = results.stream()
            .map(this::mapToEnhancedDocumentResponse)
            .collect(Collectors.toList());
        
        return new EnhancedAttachedDocumentsResponse(profileFieldId, appId, enhancedDocs);
    }

    @Override
    @Transactional
    @Audited(action = "ATTACH_DOCUMENT_TO_FIELD", subjectType = "profile_field", subject = "#profileFieldId",
             context = {"appId=#appId"})
    public EvidenceWithDocumentResponse attachDocumentToField(String appId, String profileFieldId, DocumentResponse document) {
        log.debug("Attaching document {} to profile field {} in app {}", document.documentId(), profileFieldId, appId);
        
        if (!appId.equals(document.appId())) {
            throw new IllegalArgumentException("Document does not belong to app: " + appId);
        }
        
        // Create evidence linking the document to the profile field
        CreateEvidenceRequest evidenceRequest = new CreateEvidenceRequest(
            profileFieldId,
            document.canonicalUrl(),
            "document",
            "manual",
            null, // submittedBy - could be extracted from security context
            OffsetDateTime.now(),
            null, // validUntil
            null, // relatedEvidenceFields
            null, // trackId
            document.documentId(),
            null  // docVersionId
        );
        
        Evidence evidence = createEvidence(appId, evidenceRequest);
        
        // Trigger evidence-field attachment workflow for auto-risk creation
        AttachEvidenceToFieldRequest attachRequest = new AttachEvidenceToFieldRequest(
            "SYSTEM_DOCUMENT_ATTACHMENT", 
            "Document attached via frontend"
        );
        
        EvidenceFieldLinkResponse linkResponse = evidenceFieldLinkService.attachEvidenceToField(
            evidence.evidenceId(), 
            profileFieldId, 
            appId, 
            attachRequest
        );
        
        // Return response with both evidence and document for audit persistence
        return new EvidenceWithDocumentResponse(
            evidence.evidenceId(),
            evidence.appId(),
            evidence.profileFieldId(),
            evidence.claimId(),
            evidence.uri(),
            evidence.type(),
            evidence.sha256(),
            evidence.sourceSystem(),
            evidence.submittedBy(),
            evidence.validFrom(),
            evidence.validUntil(),
            evidence.status(),
            evidence.revokedAt(),
            evidence.reviewedBy(),
            evidence.reviewedAt(),
            evidence.relatedEvidenceFields(),
            evidence.trackId(),
            evidence.documentId(),
            evidence.docVersionId(),
            evidence.addedAt(),
            evidence.createdAt(),
            evidence.updatedAt(),
            document,
            // Auto-risk creation fields from evidence-field link workflow
            linkResponse.riskWasCreated(),
            linkResponse.autoCreatedRiskId(),
            linkResponse.assignedSme()
        );
    }

    @Override
    @Transactional
    @Audited(action = "DETACH_DOCUMENT_FROM_FIELD", subjectType = "profile_field", subject = "#profileFieldId",
             context = {"appId=#appId"})
    public EvidenceWithDocumentResponse detachDocumentFromField(String appId, String profileFieldId, DocumentResponse document) {
        log.debug("Detaching document {} from profile field {} in app {}", document.documentId(), profileFieldId, appId);
        
        if (!appId.equals(document.appId())) {
            throw new IllegalArgumentException("Document does not belong to app: " + appId);
        }
        
        // Find and store evidence information before deletion
        List<com.example.onboarding.dto.evidence.EnhancedEvidenceSummary> existingEvidence = evidenceRepository.findEvidenceByProfileField(profileFieldId, 100, 0)
            .stream()
            .filter(e -> document.documentId().equals(e.documentId()))
            .toList();
        
        if (existingEvidence.isEmpty()) {
            throw new IllegalArgumentException("No evidence found linking document " + document.documentId() + 
                " to profile field " + profileFieldId + " in app " + appId);
        }
        
        // Use the first evidence record for response (there should typically be only one)
        com.example.onboarding.dto.evidence.EnhancedEvidenceSummary evidenceToDelete = existingEvidence.get(0);
        
        // Delete evidence records that link this document to this profile field
        int deletedCount = evidenceRepository.deleteByAppIdProfileFieldIdAndDocumentId(appId, profileFieldId, document.documentId());
        
        log.debug("Deleted {} evidence records linking document {} to profile field {}", 
            deletedCount, document.documentId(), profileFieldId);
        
        // Return response with evidence and document information for audit
        return new EvidenceWithDocumentResponse(
            evidenceToDelete.evidenceId(),
            evidenceToDelete.appId(),
            evidenceToDelete.profileFieldId(),
            evidenceToDelete.claimId(),
            evidenceToDelete.uri(),
            evidenceToDelete.type(),
            null, // sha256 not available in summary
            null, // sourceSystem not available in summary  
            evidenceToDelete.submittedBy(),
            evidenceToDelete.validFrom(),
            evidenceToDelete.validUntil(),
            evidenceToDelete.status(),
            null, // revokedAt
            null, // reviewedBy
            null, // reviewedAt
            null, // relatedEvidenceFields not available in summary
            evidenceToDelete.trackId(),
            evidenceToDelete.documentId(),
            null, // docVersionId not available in summary
            null, // addedAt not available in summary
            evidenceToDelete.createdAt(),
            evidenceToDelete.updatedAt(),
            document,
            // Auto-risk creation fields - not applicable for detachment
            false,
            null,
            null
        );
    }

    @Override
    public EvidenceFieldLinkResponse attachEvidenceToProfileField(String evidenceId, String profileFieldId, 
                                                                 String appId, AttachEvidenceToFieldRequest request) {
        log.debug("Attaching evidence {} to profile field {} in app {}", evidenceId, profileFieldId, appId);
        return evidenceFieldLinkService.attachEvidenceToField(evidenceId, profileFieldId, appId, request);
    }

    @Override
    public void detachEvidenceFromProfileField(String evidenceId, String profileFieldId) {
        log.debug("Detaching evidence {} from profile field {}", evidenceId, profileFieldId);
        evidenceFieldLinkService.detachEvidenceFromField(evidenceId, profileFieldId);
    }

    @Override
    public EvidenceUsageResponse getEvidenceUsage(String evidenceId) {
        log.debug("Getting evidence usage for {}", evidenceId);
        
        Evidence evidence = getEvidenceById(evidenceId)
                .orElseThrow(() -> new RuntimeException("Evidence not found with id: " + evidenceId));

        // Get field links
        List<EvidenceFieldLinkResponse> fieldLinks = evidenceFieldLinkService.getEvidenceFieldLinks(evidenceId);
        List<EvidenceUsageResponse.FieldUsage> fieldUsages = fieldLinks.stream()
                .map(link -> new EvidenceUsageResponse.FieldUsage(
                        link.profileFieldId(),
                        extractFieldKeyFromProfileFieldId(link.profileFieldId()),
                        link.appId(),
                        link.linkStatus().name(),
                        link.linkedBy(),
                        link.linkedAt() != null ? link.linkedAt().toString() : null
                ))
                .collect(Collectors.toList());

        // TODO: Get risk usages when risk story evidence repository is available
        List<EvidenceUsageResponse.RiskUsage> riskUsages = new ArrayList<>();

        return new EvidenceUsageResponse(
                evidenceId,
                "Evidence " + evidenceId,  // Evidence record doesn't have title field
                evidence.uri(),
                fieldUsages,
                riskUsages
        );
    }

    @Override
    public EvidenceFieldLinkResponse reviewEvidenceFieldLink(String evidenceId, String profileFieldId, 
                                                            String reviewedBy, String comment, boolean approved) {
        log.debug("Reviewing evidence field link: {} -> {}, approved: {}", evidenceId, profileFieldId, approved);
        return evidenceFieldLinkService.reviewEvidenceFieldLink(evidenceId, profileFieldId, reviewedBy, comment, approved);
    }

    private String extractFieldKeyFromProfileFieldId(String profileFieldId) {
        // Extract field key from profile field ID pattern (appId_fieldKey_xxx)
        String[] parts = profileFieldId.split("_");
        if (parts.length >= 2) {
            return parts[1];
        }
        return "unknown";
    }

    /**
     * Map database result to EnhancedDocumentResponse with attachment info
     */
    private EnhancedDocumentResponse mapToEnhancedDocumentResponse(Map<String, Object> row) {
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
            true, // isAttachedToField - this method is only called for attached docs
            convertToOffsetDateTime(row.get("attached_at")),
            (String) row.get("evidence_id"),
            (String) row.get("source_system"),
            (String) row.get("submitted_by")
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
    
    @Override
    public List<com.example.onboarding.dto.evidence.EnhancedEvidenceSummary> searchEvidence(
            String linkStatus, String appId, String fieldKey, String assignedPo, 
            String assignedSme, String evidenceStatus, String documentSourceType, 
            int page, int size) {
        
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        int offset = safePage * safeSize;
        
        return evidenceRepository.searchEvidence(linkStatus, appId, fieldKey, assignedPo, 
                                                assignedSme, evidenceStatus, documentSourceType, 
                                                safeSize, offset);
    }
}