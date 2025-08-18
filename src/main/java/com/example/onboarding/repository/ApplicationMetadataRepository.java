package com.example.onboarding.repository;

import com.example.onboarding.model.ApplicationMetadata;
import com.example.onboarding.model.EnvironmentInstance;

import java.util.List;
import java.util.Optional;

public interface ApplicationMetadataRepository {
    Optional<ApplicationMetadata> findByAppId(String appId);
    List<ApplicationMetadata> findChildren(String parentAppId);
    List<EnvironmentInstance> findEnvironmentsByAppId(String appId);


}
