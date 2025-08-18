package com.example.onboarding.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class DeliveryUnit {
    private String appId;
    private String appName;
    private List<Tool> versionControlTools;
    private List<Tool> workManagementTools;
    private List<Tool> serviceManagementTools;
    private List<Contact> contacts;
    private ArtifactLinks artifacts;

    public DeliveryUnit(ApplicationMetadata app,
                        ToolMappings mappings,
                        List<Contact> contacts,
                        ArtifactLinks artifacts) {
        this.appId = app.getAppId();
        this.appName = app.getAppName();
        this.versionControlTools = mappings.getVersionControlTools();
        this.workManagementTools = mappings.getWorkManagementTools();
        this.serviceManagementTools = mappings.getServiceManagementTools();
        this.contacts = contacts;
        this.artifacts = artifacts;
    }
}
