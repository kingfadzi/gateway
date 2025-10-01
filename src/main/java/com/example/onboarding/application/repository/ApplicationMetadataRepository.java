package com.example.onboarding.application.repository;

import com.example.onboarding.application.model.ApplicationMetadata;
import com.example.onboarding.deliveryunit.dto.EnvironmentInstance;

import java.util.List;
import java.util.Optional;

public interface ApplicationMetadataRepository {
    Optional<ApplicationMetadata> findByAppId(String appId);
    List<ApplicationMetadata> findChildren(String parentAppId);
    List<EnvironmentInstance> findEnvironmentsByAppId(String appId);


}
