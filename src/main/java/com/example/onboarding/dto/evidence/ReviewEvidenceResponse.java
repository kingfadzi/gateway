package com.example.onboarding.dto.evidence;

import java.time.OffsetDateTime;
import java.util.List;

public record ReviewEvidenceResponse(
    String evidenceId,
    String status,                 // active | superseded | revoked
    OffsetDateTime reviewedAt,
    String reviewedBy,
    List<String> supersededEvidenceIds
) {}
