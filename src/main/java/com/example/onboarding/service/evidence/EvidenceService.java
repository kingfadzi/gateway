package com.example.onboarding.service.evidence;

import com.example.onboarding.dto.PageResponse;
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
import com.example.onboarding.dto.document.DocumentResponse;
import com.example.onboarding.dto.evidence.EvidenceSearchRequest;
import com.example.onboarding.dto.evidence.WorkbenchEvidenceItem;
import java.util.List;
import java.util.Map;

import java.util.Optional;

public interface EvidenceService {
    
    /**
     * Create new evidence
     */
    Evidence createEvidence(String appId, CreateEvidenceRequest request);
    
    /**
     * Update existing evidence
     */
    Evidence updateEvidence(String evidenceId, UpdateEvidenceRequest request);
    
    /**
     * Get evidence by ID
     */
    Optional<Evidence> getEvidenceById(String evidenceId);
    
    /**
     * List evidence for an application with pagination
     */
    PageResponse<com.example.onboarding.dto.evidence.EnhancedEvidenceSummary> getEvidenceByApp(String appId, int page, int pageSize);
    
    /**
     * List evidence for a profile field with pagination
     */
    PageResponse<com.example.onboarding.dto.evidence.EnhancedEvidenceSummary> getEvidenceByProfileField(String profileFieldId, int page, int pageSize);
    
    
    /**
     * List evidence for a claim with pagination
     */
    PageResponse<com.example.onboarding.dto.evidence.EnhancedEvidenceSummary> getEvidenceByClaim(String claimId, int page, int pageSize);
    
    /**
     * List evidence for a track with pagination
     */
    PageResponse<com.example.onboarding.dto.evidence.EnhancedEvidenceSummary> getEvidenceByTrack(String trackId, int page, int pageSize);
    
    /**
     * Revoke evidence
     */
    Evidence revokeEvidence(String evidenceId, String reviewedBy);
    
    /**
     * Create evidence with a new document in a single atomic operation
     */
    EvidenceWithDocumentResponse createEvidenceWithDocument(String appId, CreateEvidenceWithDocumentRequest request);
    
    /**
     * Get all documents currently attached as evidence to a profile field
     */
    AttachedDocumentsResponse getAttachedDocuments(String appId, String profileFieldId);
    
    /**
     * Get enhanced attached documents with full document metadata
     */
    EnhancedAttachedDocumentsResponse getEnhancedAttachedDocuments(String appId, String profileFieldId);
    
    /**
     * Attach an existing document as evidence to a profile field
     */
    EvidenceWithDocumentResponse attachDocumentToField(String appId, String profileFieldId, DocumentResponse document);
    
    /**
     * Detach a document from a profile field by removing the evidence link
     */
    EvidenceWithDocumentResponse detachDocumentFromField(String appId, String profileFieldId, DocumentResponse document);
    
    /**
     * Attach evidence to a profile field with workflow management
     */
    EvidenceFieldLinkResponse attachEvidenceToProfileField(String evidenceId, String profileFieldId, 
                                                          String appId, AttachEvidenceToFieldRequest request);
    
    /**
     * Detach evidence from a profile field
     */
    void detachEvidenceFromProfileField(String evidenceId, String profileFieldId);
    
    /**
     * Get evidence usage across fields and risks
     */
    EvidenceUsageResponse getEvidenceUsage(String evidenceId);
    
    /**
     * Review an evidence-field link
     */
    EvidenceFieldLinkResponse reviewEvidenceFieldLink(String evidenceId, String profileFieldId, 
                                                     String reviewedBy, String comment, boolean approved);
    
    /**
     * Search evidence with multiple filters
     */
    List<com.example.onboarding.dto.evidence.EnhancedEvidenceSummary> searchEvidence(
        String linkStatus, String appId, String fieldKey, String assignedPo, 
        String assignedSme, String evidenceStatus, String documentSourceType, 
        int page, int size);

    /**
     * Search evidence for the compliance workbench
     */
    List<WorkbenchEvidenceItem> searchWorkbenchEvidence(EvidenceSearchRequest request);

    /**
     * Get evidence by KPI state: COMPLIANT
     */
    List<com.example.onboarding.dto.evidence.KpiEvidenceSummary> getCompliantEvidence(String appId, int page, int size);

    /**
     * Get evidence by KPI state: PENDING REVIEW
     */
    List<com.example.onboarding.dto.evidence.KpiEvidenceSummary> getPendingReviewEvidence(String appId, int page, int size);

    /**
     * Get profile fields by KPI state: MISSING EVIDENCE
     */
    List<Map<String, Object>> getMissingEvidenceFields(String appId, int page, int size);

    /**
     * Get risks by KPI state: RISK BLOCKED
     */
    List<Map<String, Object>> getRiskBlockedItems(String appId, int page, int size);
}