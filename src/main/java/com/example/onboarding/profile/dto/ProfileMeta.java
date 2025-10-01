package com.example.onboarding.profile.dto;

import java.time.OffsetDateTime;

public record ProfileMeta(String profileId, int version, OffsetDateTime updatedAt) {}