package com.example.onboarding.dto.profile;

import java.time.OffsetDateTime;

public record ProfileMeta(String profileId, int version, OffsetDateTime updatedAt) {}