package com.example.onboarding.service;

import com.example.onboarding.model.*;
import org.springframework.stereotype.Service;

@Service
public class DeliveryUnitBuilder {

    private final ApplicationMetadataService appService;
    private final ComponentMappingService mappingService;

    public DeliveryUnitBuilder(ApplicationMetadataService appService, ComponentMappingService mappingService) {
        this.appService = appService;
        this.mappingService = mappingService;
    }

    public DeliveryUnit build(DeliveryUnitRequest request) {
        ApplicationMetadata appMeta = appService.findByAppId(request.getAppId())
                .orElseThrow(() -> new IllegalArgumentException("App ID not found: " + request.getAppId()));

        String componentId = appMeta.getComponentId();
        if (componentId == null || componentId.isBlank()) {
            throw new IllegalStateException("Component ID not resolved for App ID: " + request.getAppId());
        }

        ToolMappings mappings = mappingService.resolveMappings(componentId);

        return new DeliveryUnit(
                appMeta,
                mappings,
                request.getContacts(),
                request.getArtifacts()
        );
    }

}
