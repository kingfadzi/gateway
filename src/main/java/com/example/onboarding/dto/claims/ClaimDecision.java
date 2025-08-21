// src/main/java/com/example/onboarding/dto/claims/ClaimDecision.java
package com.example.onboarding.dto.claims;

import com.example.onboarding.dto.evidence.ReuseCandidate;

import java.util.List;

public record ClaimDecision(
        boolean acceptable,
        List<String> reasons,
        boolean saved,
        String claimId,
        ReuseCandidate appliedEvidence
) {}