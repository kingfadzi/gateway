package com.example.onboarding.dto.attestation;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record IndividualAttestationRequest(
    String profileFieldId,
    String evidenceId,
    String attestationType,
    String attestationComments,
    String attestedBy
) {
    // Validation helper methods
    public boolean isValid() {
        return profileFieldId != null && !profileFieldId.isBlank() &&
               attestedBy != null && !attestedBy.isBlank() &&
               attestationType != null && !attestationType.isBlank();
    }
    
    public boolean isValidAttestationType() {
        return "compliance".equals(attestationType) || 
               "exception".equals(attestationType) || 
               "remediation".equals(attestationType);
    }
}