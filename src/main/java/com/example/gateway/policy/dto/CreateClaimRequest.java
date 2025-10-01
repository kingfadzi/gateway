package com.example.gateway.policy.dto;

import java.time.OffsetDateTime;

public record CreateClaimRequest(
        String requirementId,
        String releaseId,
        String evidenceId,
        String method,                // manual | auto | imported
        String profileFieldExpected,  // optional check
        String typeExpected,          // optional check
        OffsetDateTime releaseWindowStart // optional cutoff
) {}
