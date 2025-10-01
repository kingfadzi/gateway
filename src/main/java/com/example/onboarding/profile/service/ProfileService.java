package com.example.onboarding.profile.service;

import com.example.onboarding.profile.dto.DomainGraphPayload;
import com.example.onboarding.profile.dto.PatchProfileRequest;
import com.example.onboarding.profile.dto.PatchProfileResponse;
import com.example.onboarding.profile.dto.ProfileFieldContext;
import com.example.onboarding.profile.dto.ProfilePayload;
import com.example.onboarding.profile.dto.ProfileSnapshotDto;
import com.example.onboarding.profile.dto.SuggestedEvidence;

public interface ProfileService {
    ProfileSnapshotDto getProfile(String appId);
    
    ProfilePayload getProfilePayload(String appId);
    
    DomainGraphPayload getProfileDomainGraph(String appId);

    ProfileFieldContext getProfileFieldContext(String appId, String fieldKey);

    SuggestedEvidence getSuggestedEvidence(String appId, String fieldKey);

    PatchProfileResponse patchProfile(String appId, PatchProfileRequest req);

    String getFieldKeyByProfileFieldId(String profileFieldId);

}
