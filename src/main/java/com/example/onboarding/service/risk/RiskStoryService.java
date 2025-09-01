package com.example.onboarding.service.risk;

import com.example.onboarding.dto.risk.AttachEvidenceRequest;
import com.example.onboarding.dto.risk.CreateRiskStoryRequest;
import com.example.onboarding.dto.risk.RiskStoryResponse;
import com.example.onboarding.dto.risk.RiskStoryEvidenceResponse;

public interface RiskStoryService {

    RiskStoryResponse createRiskStory(String appId, String fieldKey, CreateRiskStoryRequest request);

    RiskStoryEvidenceResponse attachEvidence(String riskId, AttachEvidenceRequest request);

    void detachEvidence(String riskId, String evidenceId);
}
