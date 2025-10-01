package com.example.gateway.evidence.service;

import com.example.gateway.application.dto.attestation.BulkAttestationRequest;
import com.example.gateway.application.dto.attestation.BulkAttestationResponse;

import java.util.List;

public interface FieldAttestationService {
    
    void processFieldAttestation(String appId,
                               BulkAttestationRequest.FieldAttestationRequest fieldRequest,
                               String attestedBy, 
                               String comments,
                               List<BulkAttestationResponse.AttestationSuccess> successful,
                               List<BulkAttestationResponse.AttestationFailure> failed);
}