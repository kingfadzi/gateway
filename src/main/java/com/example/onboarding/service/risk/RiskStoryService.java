package com.example.onboarding.service.risk;

import com.example.onboarding.dto.risk.AttachEvidenceRequest;
import com.example.onboarding.dto.risk.CreateRiskStoryRequest;
import com.example.onboarding.dto.risk.RiskStoryResponse;
import com.example.onboarding.dto.risk.RiskStoryEvidenceResponse;

import java.util.List;

public interface RiskStoryService {

    RiskStoryResponse createRiskStory(String appId, String fieldKey, CreateRiskStoryRequest request);

    RiskStoryEvidenceResponse attachEvidence(String riskId, AttachEvidenceRequest request);

    void detachEvidence(String riskId, String evidenceId);

    List<RiskStoryResponse> getRisksByAppId(String appId);
    
    RiskStoryResponse getRiskById(String riskId);
    
    List<RiskStoryResponse> getRisksByAppIdAndFieldKey(String appId, String fieldKey);
    
    List<RiskStoryResponse> getRisksByProfileFieldId(String profileFieldId);
    
    List<RiskStoryResponse> searchRisks(String appId, String assignedSme, String status, 
                                       String domain, String derivedFrom, String fieldKey, 
                                       String severity, String creationType, String triggeringEvidenceId,
                                       String sortBy, String sortOrder, int page, int size);
}
