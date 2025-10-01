package com.example.onboarding.profile.dto;

import java.util.Map;

public record FieldRegistryItem(
        String key,
        String derivedFrom,
        Map<String, Object> rule) {
}