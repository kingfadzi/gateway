package com.example.onboarding.service.sme;

import com.example.onboarding.dto.sme.SmeRiskQueueResponse;

import java.util.List;

public interface SmeRiskService {
    
    /**
     * Get risks pending SME review assigned to current user
     * For the "My Review Queue" table
     */
    List<SmeRiskQueueResponse> getMyReviewQueue(String smeId);
    
}