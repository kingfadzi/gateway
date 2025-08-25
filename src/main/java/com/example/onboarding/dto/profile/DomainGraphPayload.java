package com.example.onboarding.dto.profile;

import java.time.OffsetDateTime;
import java.util.List;

public record DomainGraphPayload(
        String appId,
        String name,
        int version,
        OffsetDateTime updatedAt,
        List<DomainPayload> domains
) {
}