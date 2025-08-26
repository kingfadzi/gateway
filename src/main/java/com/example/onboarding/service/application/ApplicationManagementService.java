package com.example.onboarding.service.application;

import com.example.onboarding.dto.*;
import com.example.onboarding.dto.application.Application;
import com.example.onboarding.dto.application.ChildApplication;

import java.util.List;

public interface ApplicationManagementService {
    PageResponse<Application> list(
            String q,
            String ownerId,
            String onboardingStatus,
            String operationalStatus,
            String parentAppId,
            String sort,
            int page,
            int pageSize
    );

    Application get(String appId);

    Application create(CreateAppRequest req);

    Application patch(String appId, UpdateAppRequest req);

    void delete(String appId, boolean soft);
    
    List<ChildApplication> getChildren(String parentAppId);
}
