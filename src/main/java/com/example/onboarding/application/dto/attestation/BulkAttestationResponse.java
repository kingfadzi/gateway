package com.example.onboarding.application.dto.attestation;

import java.util.List;

/**
 * Response DTO for bulk attestation endpoint
 */
public record BulkAttestationResponse(
    List<AttestationSuccess> successful,
    List<AttestationFailure> failed,
    AttestationSummary summary
) {
    
    /**
     * Successful attestation result
     */
    public record AttestationSuccess(
        String profileFieldId,
        String fieldKey,
        String attestationId
    ) {}
    
    /**
     * Failed attestation result with error details
     */
    public record AttestationFailure(
        String profileFieldId,
        String fieldKey,
        String error,
        String reason
    ) {}
    
    /**
     * Summary statistics for the bulk operation
     */
    public record AttestationSummary(
        int total,
        int successful,
        int failed
    ) {}
    
    /**
     * Create response from success and failure lists
     */
    public static BulkAttestationResponse create(List<AttestationSuccess> successful, List<AttestationFailure> failed) {
        int totalCount = successful.size() + failed.size();
        AttestationSummary summary = new AttestationSummary(totalCount, successful.size(), failed.size());
        return new BulkAttestationResponse(successful, failed, summary);
    }
}