package com.example.onboarding.service.profile;

import com.example.onboarding.dto.PageResponse;
import com.example.onboarding.dto.profile.DomainGraphPayload;
import com.example.onboarding.dto.profile.PatchProfileRequest;
import com.example.onboarding.dto.profile.PatchProfileResponse;
import com.example.onboarding.dto.profile.ProfileFieldContext;
import com.example.onboarding.dto.profile.ProfilePayload;
import com.example.onboarding.dto.profile.ProfileSnapshotDto;
import com.example.onboarding.dto.profile.SuggestedEvidence;

public interface ProfileService {
    ProfileSnapshotDto getProfile(String appId);
    
    ProfilePayload getProfilePayload(String appId);
    
    DomainGraphPayload getProfileDomainGraph(String appId);

    ProfileFieldContext getProfileFieldContext(String appId, String fieldKey);

    SuggestedEvidence getSuggestedEvidence(String appId, String fieldKey);

    PatchProfileResponse patchProfile(String appId, PatchProfileRequest req);

    String getFieldKeyByProfileFieldId(String profileFieldId);

}
