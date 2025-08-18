package com.example.onboarding.repository;

import com.example.onboarding.model.Tool;
import com.example.onboarding.model.ToolMappings;

import java.util.List;

public interface ComponentMappingRepository {
    ToolMappings resolveMappings(String componentId);

    List<Tool> findByComponentIdAndToolType(String componentId, String toolType);

    Long findComponentIdByAppId(String appId);
    List<Tool> findToolsByMappingType(Long componentId, String mappingType);

    List<Tool> findAllTools(Long componentId);
}
