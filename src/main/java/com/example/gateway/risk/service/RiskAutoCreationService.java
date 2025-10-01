package com.example.gateway.risk.service;

import com.example.gateway.risk.dto.AutoRiskCreationResponse;

public interface RiskAutoCreationService {
    
    AutoRiskCreationResponse evaluateAndCreateRisk(String evidenceId, String profileFieldId, String appId);
    
    String getFieldKeyFromProfileFieldId(String profileFieldId);
    
    String getAppRatingForField(String appId, String fieldKey);
    
    String assignSmeForRisk(String appId, String fieldKey);
}