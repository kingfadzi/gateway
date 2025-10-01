package com.example.onboarding.profile.dto;

import java.time.OffsetDateTime;

public class Profile {
    String profileId;
    String scopeType;
    String scopeId;
    int version;
    OffsetDateTime snapshotAt;
    OffsetDateTime updatedAt;
    OffsetDateTime createdAt;

}
