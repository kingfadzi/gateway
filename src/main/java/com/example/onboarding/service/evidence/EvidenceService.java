package com.example.onboarding.service.evidence;

import com.example.onboarding.dto.PageResponse;
import com.example.onboarding.dto.evidence.CreateEvidenceRequest;
import com.example.onboarding.dto.evidence.CreateEvidenceWithDocumentRequest;
import com.example.onboarding.dto.evidence.Evidence;
import com.example.onboarding.dto.evidence.EvidenceSummary;
import com.example.onboarding.dto.evidence.EvidenceWithDocumentResponse;
import com.example.onboarding.dto.evidence.UpdateEvidenceRequest;

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
    PageResponse<EvidenceSummary> getEvidenceByApp(String appId, int page, int pageSize);
    
    /**
     * List evidence for a profile field with pagination
     */
    PageResponse<EvidenceSummary> getEvidenceByProfileField(String profileFieldId, int page, int pageSize);
    
    /**
     * List evidence for a claim with pagination
     */
    PageResponse<EvidenceSummary> getEvidenceByClaim(String claimId, int page, int pageSize);
    
    /**
     * List evidence for a track with pagination
     */
    PageResponse<EvidenceSummary> getEvidenceByTrack(String trackId, int page, int pageSize);
    
    /**
     * Revoke evidence
     */
    Evidence revokeEvidence(String evidenceId, String reviewedBy);
    
    /**
     * Create evidence with a new document in a single atomic operation
     */
    EvidenceWithDocumentResponse createEvidenceWithDocument(String appId, CreateEvidenceWithDocumentRequest request);
}