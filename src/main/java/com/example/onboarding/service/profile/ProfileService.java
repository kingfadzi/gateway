package com.example.onboarding.service.profile;

import com.example.onboarding.dto.PageResponse;
import com.example.onboarding.dto.evidence.CreateEvidenceRequest;
import com.example.onboarding.dto.evidence.EvidenceDto;
import com.example.onboarding.dto.profile.PatchProfileRequest;
import com.example.onboarding.dto.profile.PatchProfileResponse;
import com.example.onboarding.dto.profile.ProfileSnapshotDto;

public interface ProfileService {
    ProfileSnapshotDto getProfile(String appId);

    PatchProfileResponse patchProfile(String appId, PatchProfileRequest req);

    PageResponse<EvidenceDto> listEvidence(String appId, String fieldKey, int page, int pageSize);

    EvidenceDto addEvidence(String appId, CreateEvidenceRequest req);

    void deleteEvidence(String appId, String evidenceId);
}
