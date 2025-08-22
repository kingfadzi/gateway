package com.example.onboarding.service.application;

import com.example.onboarding.dto.*;

public interface ApplicationManagementService {
    PageResponse<ApplicationDto> list(
            String q,
            String ownerId,
            String onboardingStatus,
            String operationalStatus,
            String parentAppId,
            String sort,
            int page,
            int pageSize
    );

    ApplicationDto get(String appId);

    ApplicationDto create(CreateAppRequest req);

    ApplicationDto patch(String appId, UpdateAppRequest req);

    void delete(String appId, boolean soft);
}
