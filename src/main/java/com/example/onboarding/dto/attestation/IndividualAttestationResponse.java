package com.example.onboarding.dto.attestation;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record IndividualAttestationResponse(
    String attestationId,
    String profileFieldId,
    String status,
    String message,
    String attestedAt
) {
    
    public static IndividualAttestationResponse success(String attestationId, String profileFieldId, OffsetDateTime attestedAt) {
        return new IndividualAttestationResponse(
            attestationId,
            profileFieldId,
            "success",
            null,
            attestedAt.toString()
        );
    }
    
    public static IndividualAttestationResponse failed(String profileFieldId, String errorMessage) {
        return new IndividualAttestationResponse(
            null,
            profileFieldId,
            "failed",
            errorMessage,
            null
        );
    }
}