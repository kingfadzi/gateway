package com.example.gateway.deliveryunit.repository;

import com.example.gateway.deliveryunit.dto.Tool;
import com.example.gateway.deliveryunit.dto.ToolMappings;

import java.util.List;

public interface ComponentMappingRepository {
    ToolMappings resolveMappings(String componentId);

    List<Tool> findByComponentIdAndToolType(String componentId, String toolType);

    Long findComponentIdByAppId(String appId);
    List<Tool> findToolsByMappingType(Long componentId, String mappingType);

    List<Tool> findAllTools(Long componentId);
}
