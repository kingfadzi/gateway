package com.example.onboarding.dto.risk;

/**
 * Response when a risk is automatically created
 */
public record AutoRiskCreationResponse(
        String riskId,
        String fieldKey,
        String appId,
        String appCriticality,
        String assignedSme,
        String triggeringEvidenceId,
        String evaluationReason,
        boolean wasCreated
) {
    
    public static AutoRiskCreationResponse created(String riskId, String fieldKey, String appId, 
                                                 String appCriticality, String assignedSme, 
                                                 String evidenceId, String reason) {
        return new AutoRiskCreationResponse(
            riskId, fieldKey, appId, appCriticality, assignedSme, evidenceId, reason, true
        );
    }
    
    public static AutoRiskCreationResponse notCreated(String fieldKey, String appId, 
                                                    String appCriticality, String reason) {
        return new AutoRiskCreationResponse(
            null, fieldKey, appId, appCriticality, null, null, reason, false
        );
    }
}