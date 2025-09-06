package com.example.onboarding.service.evidence;

import com.example.onboarding.dto.attestation.BulkAttestationRequest;
import com.example.onboarding.dto.attestation.BulkAttestationResponse;

import java.util.List;

public interface FieldAttestationService {
    
    void processFieldAttestation(String appId,
                               BulkAttestationRequest.FieldAttestationRequest fieldRequest,
                               String attestedBy, 
                               String comments,
                               List<BulkAttestationResponse.AttestationSuccess> successful,
                               List<BulkAttestationResponse.AttestationFailure> failed);
}