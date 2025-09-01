package com.example.onboarding.dto.risk;

import java.util.Map;

public record CreateRiskStoryRequest(
    String title,
    String hypothesis,
    String condition,
    String consequence,
    String controlRefs,
    Map<String, Object> attributes,
    String severity,
    String status,
    String raisedBy,
    String owner,
    String trackId
) {}
