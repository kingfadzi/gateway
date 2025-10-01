package com.example.gateway.risk.dto;

public record AttachEvidenceRequest(
    String evidenceId,
    String submittedBy
) {}
