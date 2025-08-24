package com.example.onboarding.dto.profile;

import java.util.List;

public record DomainPayload(
        String domainKey,
        String title,
        String icon,
        String driverLabel,
        String driverValue,
        List<FieldGraphPayload> fields
) {
}