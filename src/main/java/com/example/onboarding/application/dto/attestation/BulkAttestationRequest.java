package com.example.onboarding.application.dto.attestation;

import java.util.List;

/**
 * Request DTO for bulk attestation endpoint
 */
public record BulkAttestationRequest(
    List<FieldAttestationRequest> fields,
    String comments,
    String attestationType,
    String attestedBy,
    String attestationComments
) {
    
    /**
     * Individual field attestation request
     */
    public record FieldAttestationRequest(
        String profileFieldId,
        String fieldKey
    ) {}
}