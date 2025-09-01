package com.example.onboarding.service.risk;

import com.example.onboarding.dto.risk.AutoRiskCreationResponse;

public interface RiskAutoCreationService {
    
    AutoRiskCreationResponse evaluateAndCreateRisk(String evidenceId, String profileFieldId, String appId);
    
    String getFieldKeyFromProfileFieldId(String profileFieldId);
    
    String getAppRatingForField(String appId, String fieldKey);
    
    String assignSmeForRisk(String appId, String fieldKey);
}