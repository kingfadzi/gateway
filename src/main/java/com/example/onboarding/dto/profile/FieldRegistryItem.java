package com.example.onboarding.dto.profile;

import java.util.Map;

public record FieldRegistryItem(
        String key,
        String derivedFrom,
        Map<String, Object> rule) {
}