package com.example.gateway.application.repository;

import com.example.gateway.application.model.ApplicationMetadata;
import com.example.gateway.deliveryunit.dto.EnvironmentInstance;

import java.util.List;
import java.util.Optional;

public interface ApplicationMetadataRepository {
    Optional<ApplicationMetadata> findByAppId(String appId);
    List<ApplicationMetadata> findChildren(String parentAppId);
    List<EnvironmentInstance> findEnvironmentsByAppId(String appId);


}
