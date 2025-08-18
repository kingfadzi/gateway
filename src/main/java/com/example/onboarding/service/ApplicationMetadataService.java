package com.example.onboarding.service;

import com.example.onboarding.model.AppMetadataResponse;
import com.example.onboarding.model.ApplicationMetadata;
import com.example.onboarding.model.EnvironmentInstance;
import com.example.onboarding.repository.ApplicationMetadataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ApplicationMetadataService {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationMetadataService.class);

    private final ApplicationMetadataRepository repo;

    public ApplicationMetadataService(ApplicationMetadataRepository repo) {
        this.repo = repo;
    }

    /**
     * Retrieve the raw ApplicationMetadata entity (optional).
     */
    public Optional<ApplicationMetadata> findByAppId(String appId) {
        try {
            return repo.findByAppId(appId);
        } catch (Exception e) {
            logger.error("Error while retrieving application metadata for appId={}", appId, e);
            return Optional.empty();
        }
    }

    /**
     * Retrieve the full metadata structure for frontend consumption.
     * Includes associated components (children). Handles empty/null safely.
     */
    public AppMetadataResponse getFullAppMetadata(String appId) {
        try {
            Optional<ApplicationMetadata> maybeApp = repo.findByAppId(appId);

            if (maybeApp.isEmpty()) {
                logger.warn("Application not found for appId={}", appId);
                return null;
            }

            ApplicationMetadata app = maybeApp.get();

            List<AppMetadataResponse.AppComponent> children =
                    Optional.ofNullable(repo.findChildren(appId))
                            .orElse(Collections.emptyList())
                            .stream()
                            .map(child -> new AppMetadataResponse.AppComponent(child.getAppName(), child.getAppId()))
                            .collect(Collectors.toList());

            AppMetadataResponse response = new AppMetadataResponse(app, children);
            logger.debug("Successfully built AppMetadataResponse for appId={}: {}", appId, response);

            return response;

        } catch (Exception e) {
            logger.error("Unexpected error while assembling metadata for appId={}", appId, e);
            throw new RuntimeException("Failed to retrieve application metadata for appId=" + appId, e);
        }
    }

    /**
     * Retrieve all environment instances for the specified application.
     */
    public List<EnvironmentInstance> getEnvironmentsForApp(String appId) {
        try {
            List<EnvironmentInstance> instances = repo.findEnvironmentsByAppId(appId);
            logger.debug("Retrieved {} environment instances for appId={}", instances.size(), appId);
            return instances;
        } catch (Exception e) {
            logger.error("Error retrieving environment instances for appId={}", appId, e);
            return Collections.emptyList();
        }
    }
}
