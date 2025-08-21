package com.example.onboarding.dto.claims;

import java.time.OffsetDateTime;
import java.util.List;

public record ClaimDto(
        String claimId,
        String requirementId,
        String releaseId,
        String evidenceId,
        boolean acceptable,
        List<String> reasons,
        String method,
        OffsetDateTime createdAt
) {}