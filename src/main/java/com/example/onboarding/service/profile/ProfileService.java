package com.example.onboarding.service.profile;

import com.example.onboarding.dto.PageResponse;
import com.example.onboarding.dto.evidence.CreateEvidenceRequest;
import com.example.onboarding.dto.evidence.Evidence;
import com.example.onboarding.dto.profile.DomainGraphPayload;
import com.example.onboarding.dto.profile.PatchProfileRequest;
import com.example.onboarding.dto.profile.PatchProfileResponse;
import com.example.onboarding.dto.profile.ProfilePayload;
import com.example.onboarding.dto.profile.ProfileSnapshotDto;

public interface ProfileService {
    ProfileSnapshotDto getProfile(String appId);
    
    ProfilePayload getProfilePayload(String appId);
    
    DomainGraphPayload getProfileDomainGraph(String appId);

    PatchProfileResponse patchProfile(String appId, PatchProfileRequest req);

    PageResponse<Evidence> listEvidence(String appId, String fieldKey, int page, int pageSize);

    Evidence addEvidence(String appId, CreateEvidenceRequest req);

    void deleteEvidence(String appId, String evidenceId);
}
