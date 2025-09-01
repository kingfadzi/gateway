package com.example.onboarding.service.evidence;

import com.example.onboarding.dto.evidence.AttachEvidenceToFieldRequest;
import com.example.onboarding.dto.evidence.EvidenceFieldLinkResponse;
import com.example.onboarding.dto.risk.AutoRiskCreationResponse;

import java.util.List;
import java.util.Optional;

public interface EvidenceFieldLinkService {
    
    EvidenceFieldLinkResponse attachEvidenceToField(String evidenceId, String profileFieldId, 
                                                   String appId, AttachEvidenceToFieldRequest request);
    
    void detachEvidenceFromField(String evidenceId, String profileFieldId);
    
    EvidenceFieldLinkResponse reviewEvidenceFieldLink(String evidenceId, String profileFieldId, 
                                                     String reviewedBy, String comment, boolean approved);
    
    List<EvidenceFieldLinkResponse> getEvidenceFieldLinks(String evidenceId);
    
    List<EvidenceFieldLinkResponse> getFieldEvidenceLinks(String profileFieldId);
    
    Optional<EvidenceFieldLinkResponse> getEvidenceFieldLink(String evidenceId, String profileFieldId);
    
    AutoRiskCreationResponse evaluateAndCreateRisk(String evidenceId, String profileFieldId, String appId);
}