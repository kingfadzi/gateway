package com.example.gateway.risk.service;

import com.example.gateway.application.dto.PageResponse;
import com.example.gateway.risk.dto.AttachEvidenceRequest;
import com.example.gateway.risk.dto.CreateRiskStoryRequest;
import com.example.gateway.risk.dto.RiskStoryResponse;
import com.example.gateway.risk.dto.RiskStoryEvidenceResponse;

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
