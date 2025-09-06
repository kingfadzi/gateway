package com.example.onboarding.service.evidence;

import com.example.onboarding.dto.evidence.AttachEvidenceToFieldRequest;
import com.example.onboarding.dto.evidence.EvidenceFieldLinkResponse;
import com.example.onboarding.dto.risk.AutoRiskCreationResponse;
import com.example.onboarding.dto.attestation.BulkAttestationRequest;
import com.example.onboarding.dto.attestation.BulkAttestationResponse;
import com.example.onboarding.dto.attestation.IndividualAttestationRequest;
import com.example.onboarding.dto.attestation.IndividualAttestationResponse;

import java.util.List;
import java.util.Optional;

public interface EvidenceFieldLinkService {
    
    EvidenceFieldLinkResponse attachEvidenceToField(String evidenceId, String profileFieldId, 
                                                   String appId, AttachEvidenceToFieldRequest request);
    
    void detachEvidenceFromField(String evidenceId, String profileFieldId);
    
    EvidenceFieldLinkResponse reviewEvidenceFieldLink(String evidenceId, String profileFieldId, 
                                                     String reviewedBy, String comment, boolean approved);
    
    EvidenceFieldLinkResponse userAttestEvidenceFieldLink(String evidenceId, String profileFieldId,
                                                         String attestedBy, String comment);
    
    List<EvidenceFieldLinkResponse> getEvidenceFieldLinks(String evidenceId);
    
    List<EvidenceFieldLinkResponse> getFieldEvidenceLinks(String profileFieldId);
    
    Optional<EvidenceFieldLinkResponse> getEvidenceFieldLink(String evidenceId, String profileFieldId);
    
    AutoRiskCreationResponse evaluateAndCreateRisk(String evidenceId, String profileFieldId, String appId);
    
    /**
     * Process bulk attestations for multiple fields
     */
    BulkAttestationResponse processBulkAttestations(String appId, String attestedBy, BulkAttestationRequest request);
    
    /**
     * Process individual attestation for a single field
     */
    IndividualAttestationResponse processIndividualAttestation(String appId, IndividualAttestationRequest request);
}