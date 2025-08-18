package com.example.onboarding.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ToolMappings {
    private List<Tool> versionControlTools;
    private List<Tool> workManagementTools;
    private List<Tool> serviceManagementTools;
}
