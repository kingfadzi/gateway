package com.example.onboarding.risk.service;

import com.example.onboarding.application.dto.PageResponse;
import com.example.onboarding.risk.dto.AttachEvidenceRequest;
import com.example.onboarding.risk.dto.CreateRiskStoryRequest;
import com.example.onboarding.risk.dto.RiskStoryResponse;
import com.example.onboarding.risk.dto.RiskStoryEvidenceResponse;

import java.util.List;

public interface RiskStoryService {

    RiskStoryResponse createRiskStory(String appId, String fieldKey, CreateRiskStoryRequest request);

    RiskStoryEvidenceResponse attachEvidence(String riskId, AttachEvidenceRequest request);

    void detachEvidence(String riskId, String evidenceId);

    List<RiskStoryResponse> getRisksByAppId(String appId);
    
    PageResponse<RiskStoryResponse> getRisksByAppIdPaginated(String appId, int page, int pageSize);
    
    RiskStoryResponse getRiskById(String riskId);
    
    List<RiskStoryResponse> getRisksByAppIdAndFieldKey(String appId, String fieldKey);
    
    List<RiskStoryResponse> getRisksByProfileFieldId(String profileFieldId);
    
    List<RiskStoryResponse> searchRisks(String appId, String assignedSme, String status, 
                                       String domain, String derivedFrom, String fieldKey, 
                                       String severity, String creationType, String triggeringEvidenceId,
                                       String sortBy, String sortOrder, int page, int size);
    
    PageResponse<RiskStoryResponse> searchRisksWithPagination(String appId, String assignedSme, String status, 
                                                            String domain, String derivedFrom, String fieldKey, 
                                                            String severity, String creationType, String triggeringEvidenceId,
                                                            String sortBy, String sortOrder, int page, int size);
}
