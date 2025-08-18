package com.example.onboarding.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Tool {
    private String componentId;
    private String componentName;
    private String transactionCycle;
    private String mappingType;       // e.g. version_control, work_management
    private String instanceUrl;
    private String toolType;          // e.g. GitLab, Jira
    private String toolElementId;
    private String name;
    private String identifier;
    private String webUrl;
}
