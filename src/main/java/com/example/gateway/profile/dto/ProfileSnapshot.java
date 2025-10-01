package com.example.gateway.profile.dto;

import java.util.Map;

public record ProfileSnapshot(
        String appId,
        String profileId,
        String scopeType,
        int version,
        Map<String, Object> fields // merged: normalized context + derived keys
) { }
