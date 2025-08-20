package com.example.onboarding.dto.policy;

import java.time.OffsetDateTime;
import java.util.List;

public record CreateClaimRequest(
        String requirementId,
        String releaseId,
        String evidenceId,
        String method,                // manual | auto | imported
        String profileFieldExpected,  // optional check
        String typeExpected,          // optional check
        OffsetDateTime releaseWindowStart // optional cutoff
) {}
