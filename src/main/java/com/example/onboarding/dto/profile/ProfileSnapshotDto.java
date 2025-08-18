package com.example.onboarding.dto.profile;

import java.time.OffsetDateTime;
import java.util.List;

public record ProfileSnapshotDto(
        String appId,
        String profileId,                 // was: snapshotVersion (string)
        OffsetDateTime updatedAt,
        List<ProfileFieldDto> fields
) {}
