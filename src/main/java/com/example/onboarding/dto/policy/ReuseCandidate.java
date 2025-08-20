// src/main/java/com/example/onboarding/requirements/view/ReuseCandidate.java
package com.example.onboarding.dto.policy;

import java.time.OffsetDateTime;

public record ReuseCandidate(
        String evidenceId,
        OffsetDateTime valid_from,
        OffsetDateTime valid_until,
        String confidence,        // system_verified | human_verified | self_attested | null
        String method,            // auto | manual | attested | null
        String uri,
        String sha256,
        String type,
        String source_system,
        OffsetDateTime created_at
) {}
