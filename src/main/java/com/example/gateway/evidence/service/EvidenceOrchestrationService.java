package com.example.gateway.evidence.service;

import com.example.gateway.document.dto.CreateDocumentRequest;
import com.example.gateway.document.dto.DocumentResponse;
import com.example.gateway.document.service.DocumentService;
import com.example.gateway.evidence.dto.*;
import dev.controlplane.auditkit.annotations.Audited;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Orchestration service for complex evidence workflows that span multiple services.
 * Handles multi-step operations like creating evidence with documents and triggering
 * downstream workflows (auto-risk creation, field linking, etc.).
 *
 * This service coordinates between:
 * - EvidenceService (core evidence CRUD)
 * - DocumentService (document management)
 * - EvidenceFieldLinkService (evidence-field associations and auto-risk triggers)
 */
@Service
public class EvidenceOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(EvidenceOrchestrationService.class);

    private final EvidenceService evidenceService;
    private final DocumentService documentService;
    private final EvidenceFieldLinkService evidenceFieldLinkService;

    public EvidenceOrchestrationService(
            EvidenceService evidenceService,
            DocumentService documentService,
            EvidenceFieldLinkService evidenceFieldLinkService) {
        this.evidenceService = evidenceService;
        this.documentService = documentService;
        this.evidenceFieldLinkService = evidenceFieldLinkService;
    }

    /**
     * Creates evidence with an associated document in a single transaction.
     * This orchestration method coordinates multiple services:
     * 1. Creates a document
     * 2. Creates evidence linked to that document
     * 3. Attaches evidence to a profile field
     * 4. Triggers auto-risk creation workflow
     *
     * @param appId Application ID
     * @param request Request containing document and evidence metadata
     * @return Response with created evidence, document, and auto-risk information
     */
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

            Evidence evidence = evidenceService.createEvidence(appId, evidenceRequest);
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

        if (request.document().url() == null || request.document().url().trim().isEmpty()) {
            throw new IllegalArgumentException("Document URL is required");
        }

        if (request.document().title() == null || request.document().title().trim().isEmpty()) {
            throw new IllegalArgumentException("Document title is required");
        }
    }
}
