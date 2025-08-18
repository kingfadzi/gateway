package com.example.onboarding.controller;

import com.example.onboarding.model.ApplicationMetadata;
import com.example.onboarding.model.Tool;
import com.example.onboarding.service.ApplicationMetadataService;
import com.example.onboarding.service.ComponentMappingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tools")
public class ToolController {

    private final ComponentMappingService componentMappingService;
    private final ApplicationMetadataService applicationMetadataService;

    public ToolController(
            ComponentMappingService componentMappingService,
            ApplicationMetadataService applicationMetadataService
    ) {
        this.componentMappingService = componentMappingService;
        this.applicationMetadataService = applicationMetadataService;
    }

    @GetMapping("/jira/by-app/{appId}")
    public ResponseEntity<List<Tool>> getJiraProjectsByAppId(@PathVariable String appId) {
        Long componentId = componentMappingService.resolveComponentId(appId);
        if (componentId == null) {
            throw new IllegalArgumentException("Component ID not found for App ID: " + appId);
        }

        List<Tool> jiraTools = componentMappingService.findToolsByMappingType(componentId, "work_management");
        return ResponseEntity.ok(jiraTools);
    }

    @GetMapping("/repos/by-app/{appId}")
    public ResponseEntity<List<Tool>> getReposByAppId(@PathVariable String appId) {
        Long componentId = componentMappingService.resolveComponentId(appId);
        if (componentId == null) {
            throw new IllegalArgumentException("Component ID not found for App ID: " + appId);
        }

        List<Tool> repos = componentMappingService.findToolsByMappingType(componentId, "version_control");
        return ResponseEntity.ok(repos);
    }

    @GetMapping("/service/by-app/{appId}")
    public ResponseEntity<List<Tool>> getServiceToolsByAppId(@PathVariable String appId) {
        Long componentId = componentMappingService.resolveComponentId(appId);
        if (componentId == null) {
            throw new IllegalArgumentException("Component ID not found for App ID: " + appId);
        }

        List<Tool> tools = componentMappingService.findToolsByMappingType(componentId, "service_management");
        return ResponseEntity.ok(tools);
    }

}
