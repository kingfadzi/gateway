package com.example.gateway.profile.service;

import com.example.gateway.profile.dto.DomainGraphPayload;
import com.example.gateway.profile.dto.PatchProfileRequest;
import com.example.gateway.profile.dto.PatchProfileResponse;
import com.example.gateway.profile.dto.ProfileFieldContext;
import com.example.gateway.profile.dto.ProfilePayload;
import com.example.gateway.profile.dto.ProfileSnapshotDto;
import com.example.gateway.profile.dto.SuggestedEvidence;

public interface ProfileService {
    ProfileSnapshotDto getProfile(String appId);
    
    ProfilePayload getProfilePayload(String appId);
    
    DomainGraphPayload getProfileDomainGraph(String appId);

    ProfileFieldContext getProfileFieldContext(String appId, String fieldKey);

    SuggestedEvidence getSuggestedEvidence(String appId, String fieldKey);

    PatchProfileResponse patchProfile(String appId, PatchProfileRequest req);

    String getFieldKeyByProfileFieldId(String profileFieldId);

}
