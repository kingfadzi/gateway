package com.example.gateway.risk.service;

import com.example.gateway.risk.dto.SmeRiskQueueResponse;

import java.util.List;

public interface SmeRiskService {
    
    /**
     * Get risks pending SME review assigned to current user
     * For the "My Review Queue" table
     */
    List<SmeRiskQueueResponse> getMyReviewQueue(String smeId);
    
}
