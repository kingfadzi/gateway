package com.example.onboarding.deliveryunit.service;

import com.example.onboarding.application.service.ApplicationMetadataService;
import com.example.onboarding.application.model.ApplicationMetadata;
import com.example.onboarding.deliveryunit.dto.DeliveryUnit;
import com.example.onboarding.deliveryunit.dto.DeliveryUnitRequest;
import com.example.onboarding.deliveryunit.dto.ToolMappings;
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
