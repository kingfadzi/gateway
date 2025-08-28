package com.example.onboarding.service.evidence;

import com.example.onboarding.dto.PageResponse;
import com.example.onboarding.dto.document.CreateDocumentRequest;
import com.example.onboarding.dto.document.DocumentResponse;
import com.example.onboarding.dto.evidence.CreateEvidenceRequest;
import com.example.onboarding.dto.evidence.CreateEvidenceWithDocumentRequest;
import com.example.onboarding.dto.evidence.Evidence;
import com.example.onboarding.dto.evidence.EvidenceSummary;
import com.example.onboarding.dto.evidence.EvidenceWithDocumentResponse;
import com.example.onboarding.dto.evidence.UpdateEvidenceRequest;
import com.example.onboarding.repository.evidence.EvidenceRepository;
import com.example.onboarding.service.document.DocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class EvidenceServiceImpl implements EvidenceService {
    
    private static final Logger log = LoggerFactory.getLogger(EvidenceServiceImpl.class);
    
    private final EvidenceRepository evidenceRepository;
    private final DocumentService documentService;
    
    // Valid enum values for validation
    private static final Set<String> VALID_STATUSES = Set.of("active", "superseded", "revoked");
    
    public EvidenceServiceImpl(EvidenceRepository evidenceRepository, DocumentService documentService) {
        this.evidenceRepository = evidenceRepository;
        this.documentService = documentService;
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
    public PageResponse<EvidenceSummary> getEvidenceByApp(String appId, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        int offset = (safePage - 1) * safePageSize;
        
        long total = evidenceRepository.countEvidenceByApp(appId);
        List<EvidenceSummary> evidence = evidenceRepository.findEvidenceByApp(appId, safePageSize, offset);
        
        return new PageResponse<>(safePage, safePageSize, total, evidence);
    }
    
    @Override
    public PageResponse<EvidenceSummary> getEvidenceByProfileField(String profileFieldId, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        int offset = (safePage - 1) * safePageSize;
        
        // Note: We don't have a count method for profile field, so we'll use the list size as approximation
        List<EvidenceSummary> evidence = evidenceRepository.findEvidenceByProfileField(profileFieldId, safePageSize, offset);
        
        return new PageResponse<>(safePage, safePageSize, evidence.size(), evidence);
    }
    
    @Override
    public PageResponse<EvidenceSummary> getEvidenceByClaim(String claimId, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        int offset = (safePage - 1) * safePageSize;
        
        List<EvidenceSummary> evidence = evidenceRepository.findEvidenceByClaim(claimId, safePageSize, offset);
        
        return new PageResponse<>(safePage, safePageSize, evidence.size(), evidence);
    }
    
    @Override
    public PageResponse<EvidenceSummary> getEvidenceByTrack(String trackId, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        int offset = (safePage - 1) * safePageSize;
        
        List<EvidenceSummary> evidence = evidenceRepository.findEvidenceByTrack(trackId, safePageSize, offset);
        
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
    public EvidenceWithDocumentResponse createEvidenceWithDocument(String appId, CreateEvidenceWithDocumentRequest request) {
        log.debug("Creating evidence with document for app {}: {}", appId, request);
        
        // Validate required fields
        validateCreateEvidenceWithDocumentRequest(appId, request);
        
        try {
            // Step 1: Create the document first
            CreateDocumentRequest documentRequest = new CreateDocumentRequest(
                request.document().title(),
                request.document().url(),
                request.document().fieldTypes()
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
            
            // Step 3: Build response with both evidence and document
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
                document
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
}