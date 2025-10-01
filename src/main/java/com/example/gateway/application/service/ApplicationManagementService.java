package com.example.gateway.application.service;

import com.example.gateway.application.dto.CreateAppRequest;
import com.example.gateway.application.dto.PageResponse;
import com.example.gateway.application.dto.UpdateAppRequest;
import com.example.gateway.application.dto.Application;
import com.example.gateway.application.dto.ChildApplication;

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
