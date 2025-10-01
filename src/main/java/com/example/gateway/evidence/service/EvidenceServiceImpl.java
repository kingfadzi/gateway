package com.example.gateway.evidence.service;

import com.example.gateway.config.FieldRegistryConfig;
import com.example.gateway.evidence.dto.*;
import com.example.gateway.application.dto.PageResponse;
import com.example.gateway.document.dto.CreateDocumentRequest;
import com.example.gateway.document.dto.DocumentResponse;
import com.example.gateway.document.dto.DocumentVersionInfo;
import com.example.gateway.document.dto.EnhancedDocumentResponse;
import com.example.gateway.evidence.repository.EvidenceRepository;
import com.example.gateway.profile.respository.ProfileRepository;
import com.example.gateway.profile.dto.ProfileField;
import com.example.gateway.document.service.DocumentService;
import dev.controlplane.auditkit.annotations.Audited;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EvidenceServiceImpl implements EvidenceService {
    
    private static final Logger log = LoggerFactory.getLogger(EvidenceServiceImpl.class);
    
    private final EvidenceRepository evidenceRepository;
    private final DocumentService documentService;
    private final EvidenceFieldLinkService evidenceFieldLinkService;
    private final ProfileRepository profileRepository;
    private final UnifiedFreshnessCalculator unifiedFreshnessCalculator;
    private final FieldRegistryConfig fieldRegistryConfig;
    
    
    public EvidenceServiceImpl(EvidenceRepository evidenceRepository, DocumentService documentService,
                               EvidenceFieldLinkService evidenceFieldLinkService, ProfileRepository profileRepository,
                               UnifiedFreshnessCalculator unifiedFreshnessCalculator, FieldRegistryConfig fieldRegistryConfig) {
        this.evidenceRepository = evidenceRepository;
        this.documentService = documentService;
        this.evidenceFieldLinkService = evidenceFieldLinkService;
        this.profileRepository = profileRepository;
        this.unifiedFreshnessCalculator = unifiedFreshnessCalculator;
        this.fieldRegistryConfig = fieldRegistryConfig;
    }
    
    @Override
    @Transactional
    public Evidence createEvidence(String appId, CreateEvidenceRequest request) {
        log.debug("Creating evidence for app {}: {}", appId, request);
        
        // Validate required fields
        validateCreateRequest(appId, request);
        
        // Set defaults
        OffsetDateTime validFrom = request.validFrom() != null ? request.validFrom() : OffsetDateTime.now();
        OffsetDateTime createdAt = OffsetDateTime.now();
        
        // IGNORE manual validUntil from request - compute from source date + TTL
        OffsetDateTime computedValidUntil = null;
        if (request.validUntil() != null) {
            log.warn("validUntil provided in request but will be computed from source date + TTL");
        }
        
        // Compute validUntil from source date + profile field TTL
        if (request.profileFieldId() != null) {
            try {
                ProfileField profileField = profileRepository.getProfileFieldById(request.profileFieldId())
                    .orElseThrow(() -> new RuntimeException("Profile field not found: " + request.profileFieldId()));
                
                computedValidUntil = unifiedFreshnessCalculator.calculateValidUntil(
                    request.docVersionId(),    // for document source date lookup
                    validFrom,                 // fallback #1
                    createdAt,                // fallback #2
                    profileField
                );
                
                log.debug("Computed validUntil: {} for evidence with docVersionId: {}", 
                         computedValidUntil, request.docVersionId());
                         
            } catch (Exception e) {
                log.warn("Failed to compute validUntil for evidence, will use null: {}", e.getMessage());
            }
        }
        
        try {
            String evidenceId = evidenceRepository.createEvidence(
                appId,
                request.profileFieldId(),
                request.uri(),
                request.type(),
                request.sourceSystem(),
                request.submittedBy(),
                validFrom,
                computedValidUntil, // ✅ Use computed value instead of request.validUntil()
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
        // Status validation removed - field deprecated
        
        // IGNORE manual fields that are now computed
        if (request.validUntil() != null) {
            log.warn("validUntil provided in update request but will be computed from source date + TTL");
        }
        if (request.status() != null) {
            log.warn("status provided in update request but status field is deprecated");
        }
        if (request.reviewedBy() != null) {
            log.warn("reviewedBy provided in update request but field is deprecated");
        }
        
        // Recompute validUntil if profile field or document changed
        OffsetDateTime computedValidUntil = existing.validUntil(); // Keep existing by default
        
        String profileFieldId = existing.profileFieldId();
        String docVersionId = request.docVersionId() != null ? request.docVersionId() : existing.docVersionId();
        OffsetDateTime validFrom = request.validFrom() != null ? request.validFrom() : existing.validFrom();
        
        if (profileFieldId != null && (request.docVersionId() != null || request.validFrom() != null)) {
            try {
                ProfileField profileField = profileRepository.getProfileFieldById(profileFieldId)
                    .orElseThrow(() -> new RuntimeException("Profile field not found: " + profileFieldId));
                
                computedValidUntil = unifiedFreshnessCalculator.calculateValidUntil(
                    docVersionId,
                    validFrom,
                    existing.createdAt(),
                    profileField
                );
                
                log.debug("Recomputed validUntil: {} for evidence update", computedValidUntil);
                
            } catch (Exception e) {
                log.warn("Failed to recompute validUntil for evidence update: {}", e.getMessage());
            }
        }
        
        try {
            boolean updated = evidenceRepository.updateEvidence(
                evidenceId,
                request.uri(),
                request.type(),
                request.sourceSystem(),
                request.submittedBy(),
                request.validFrom(),
                computedValidUntil, // ✅ Use computed value
                null, // ✅ Ignore status field
                null, // ✅ Ignore reviewedBy field  
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
    public PageResponse<EnhancedEvidenceSummary> getEvidenceByApp(String appId, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        int offset = (safePage - 1) * safePageSize;
        
        long total = evidenceRepository.countEvidenceByApp(appId);
        List<EnhancedEvidenceSummary> evidence = evidenceRepository.findEvidenceByApp(appId, safePageSize, offset);
        
        return new PageResponse<>(safePage, safePageSize, total, evidence);
    }
    
    @Override
    public PageResponse<EnhancedEvidenceSummary> getEvidenceByProfileField(String profileFieldId, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        int offset = (safePage - 1) * safePageSize;
        
        // Note: We don't have a count method for profile field, so we'll use the list size as approximation
        List<EnhancedEvidenceSummary> evidence = evidenceRepository.findEvidenceByProfileField(profileFieldId, safePageSize, offset);
        
        return new PageResponse<>(safePage, safePageSize, evidence.size(), evidence);
    }
    
    
    @Override
    public PageResponse<EnhancedEvidenceSummary> getEvidenceByClaim(String claimId, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        int offset = (safePage - 1) * safePageSize;
        
        List<EnhancedEvidenceSummary> evidence = evidenceRepository.findEvidenceByClaim(claimId, safePageSize, offset);
        
        return new PageResponse<>(safePage, safePageSize, evidence.size(), evidence);
    }
    
    @Override
    public PageResponse<EnhancedEvidenceSummary> getEvidenceByTrack(String trackId, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        int offset = (safePage - 1) * safePageSize;
        
        List<EnhancedEvidenceSummary> evidence = evidenceRepository.findEvidenceByTrack(trackId, safePageSize, offset);
        
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
        List<EnhancedEvidenceSummary> existingEvidence = evidenceRepository.findEvidenceByProfileField(profileFieldId, 100, 0)
            .stream()
            .filter(e -> document.documentId().equals(e.documentId()))
            .toList();
        
        if (existingEvidence.isEmpty()) {
            throw new IllegalArgumentException("No evidence found linking document " + document.documentId() + 
                " to profile field " + profileFieldId + " in app " + appId);
        }
        
        // Use the first evidence record for response (there should typically be only one)
        EnhancedEvidenceSummary evidenceToDelete = existingEvidence.get(0);
        
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
    public List<EnhancedEvidenceSummary> searchEvidence(
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

    @Override
    public List<WorkbenchEvidenceItem> searchWorkbenchEvidence(EvidenceSearchRequest request) {
        List<Map<String, Object>> results = evidenceRepository.searchWorkbenchEvidence(request);
        List<WorkbenchEvidenceItem> items = new ArrayList<>();

        for (Map<String, Object> row : results) {
            WorkbenchEvidenceItem item = new WorkbenchEvidenceItem();
            item.setEvidenceId((String) row.get("evidence_id"));
            item.setAppId((String) row.get("app_id"));
            item.setAppName((String) row.get("app_name"));
            item.setAppCriticality((String) row.get("app_criticality"));
            item.setApplicationType((String) row.get("application_type"));
            item.setArchitectureType((String) row.get("architecture_type"));
            item.setInstallType((String) row.get("install_type"));
            item.setApplicationTier((String) row.get("application_tier"));
            item.setFieldKey((String) row.get("field_key"));
            item.setFieldLabel((String) row.get("field_label"));
            item.setApprovalStatus((String) row.get("approval_status"));
            item.setAssignedReviewer((String) row.get("assigned_reviewer"));
            item.setSubmittedDate(convertToOffsetDateTime(row.get("submitted_date")));
            item.setSubmittedBy((String) row.get("submitted_by"));
            item.setUri((String) row.get("uri"));
            item.setRiskCount(((Number) row.get("risk_count")).intValue());

            // Domain Title Enrichment
            String derivedFrom = fieldRegistryConfig.getDerivedFromByFieldKey(item.getFieldKey());
            if (derivedFrom != null && !derivedFrom.equals("unknown")) {
                String domainTitle = derivedFrom.replace("_rating", "");
                item.setDomainTitle(domainTitle.substring(0, 1).toUpperCase() + domainTitle.substring(1));
            } else {
                item.setDomainTitle("Unknown");
            }

            // Freshness Calculation (Optimized)
            try {
                // Reconstruct lightweight objects for the calculator from the query results
                Evidence evidenceForFreshness = new Evidence(
                        item.getEvidenceId(), null, (String) row.get("profile_field_id"), null, null, null, null, null, null,
                        convertToOffsetDateTime(row.get("valid_from")),
                        convertToOffsetDateTime(row.get("valid_until")),
                        null, null, null, null, null, null, null,
                        (String) row.get("doc_version_id"),
                        null,
                        convertToOffsetDateTime(row.get("created_at")),
                        null
                );

                ProfileField profileFieldForFreshness = new ProfileField(
                        (String) row.get("profile_field_id"),
                        item.getFieldKey(),
                        null, // value
                        null, // sourceSystem
                        null, // sourceRef
                        0,    // evidenceCount
                        null  // updatedAt
                );

                // The 'rule' is not part of the ProfileField record, so we pass the reconstructed object
                item.setFreshnessStatus(unifiedFreshnessCalculator.calculateFreshness(evidenceForFreshness, profileFieldForFreshness));

            } catch (Exception e) {
                log.warn("Error calculating freshness for evidence {}: {}", item.getEvidenceId(), e.getMessage());
                item.setFreshnessStatus("broken");
            }


            // Days Overdue Calculation
            OffsetDateTime dueDate = item.getDueDate(); // Assuming dueDate is set from the query
            if (dueDate != null && dueDate.isBefore(OffsetDateTime.now())) {
                item.setDaysOverdue(ChronoUnit.DAYS.between(dueDate, OffsetDateTime.now()));
            } else {
                item.setDaysOverdue(0);
            }

            items.add(item);
        }
        return items;
    }

    @Override
    public PageResponse<KpiEvidenceSummary> getCompliantEvidence(
            String appId, String criticality, String domain, String fieldKey, String search, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        int offset = (safePage - 1) * safeSize;

        List<KpiEvidenceSummary> items = evidenceRepository.findCompliantEvidence(
                appId, criticality, domain, fieldKey, search, safeSize, offset);
        long total = evidenceRepository.countCompliantEvidence(appId, criticality, domain, fieldKey, search);

        return new PageResponse<>(safePage, safeSize, total, items);
    }

    @Override
    public PageResponse<KpiEvidenceSummary> getPendingReviewEvidence(
            String appId, String criticality, String domain, String fieldKey, String search, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        int offset = (safePage - 1) * safeSize;

        List<KpiEvidenceSummary> items = evidenceRepository.findPendingReviewEvidence(
                appId, criticality, domain, fieldKey, search, safeSize, offset);
        long total = evidenceRepository.countPendingReviewEvidence(appId, criticality, domain, fieldKey, search);

        return new PageResponse<>(safePage, safeSize, total, items);
    }

    @Override
    public PageResponse<Map<String, Object>> getMissingEvidenceFields(
            String appId, String criticality, String domain, String fieldKey, String search, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        int offset = (safePage - 1) * safeSize;

        List<Map<String, Object>> items = evidenceRepository.findMissingEvidenceFields(
                appId, criticality, domain, fieldKey, search, safeSize, offset);
        long total = evidenceRepository.countMissingEvidenceFields(appId, criticality, domain, fieldKey, search);

        return new PageResponse<>(safePage, safeSize, total, items);
    }

    @Override
    public PageResponse<RiskBlockedItem> getRiskBlockedItems(
            String appId, String criticality, String domain, String fieldKey, String search, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        int offset = (safePage - 1) * safeSize;

        List<RiskBlockedItem> items = evidenceRepository.findRiskBlockedItems(
                appId, criticality, domain, fieldKey, search, safeSize, offset);
        long total = evidenceRepository.countRiskBlockedItems(appId, criticality, domain, fieldKey, search);

        return new PageResponse<>(safePage, safeSize, total, items);
    }
}