package com.example.onboarding.dto.profile;// package com.example.onboarding.dto.profile;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record ProfileSnapshotDto(
        String appId,
        String profileId,
        OffsetDateTime updatedAt,
        List<ProfileField> fields,

        // NEW:
        Map<String, Object> application,
        List<Map<String, Object>> serviceInstances
) {}
