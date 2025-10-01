// src/main/java/com/example/onboarding/dto/claims/ClaimDecision.java
package com.example.onboarding.claims.dto;

import com.example.onboarding.evidence.dto.ReuseCandidate;

import java.util.List;

public record ClaimDecision(
        boolean acceptable,
        List<String> reasons,
        boolean saved,
        String claimId,
        ReuseCandidate appliedEvidence
) {}