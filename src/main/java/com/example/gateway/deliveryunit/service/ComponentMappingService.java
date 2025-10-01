package com.example.gateway.deliveryunit.service;

import com.example.gateway.deliveryunit.dto.Tool;
import com.example.gateway.deliveryunit.dto.ToolMappings;
import com.example.gateway.deliveryunit.repository.ComponentMappingRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ComponentMappingService {

    private final ComponentMappingRepository repo;

    public ComponentMappingService(ComponentMappingRepository repo) {
        this.repo = repo;
    }

    public ToolMappings resolveMappings(String componentId) {
        return repo.resolveMappings(componentId);
    }

    public List<Tool> findToolsByType(String componentId, String toolType) {
        return repo.findByComponentIdAndToolType(componentId, toolType);
    }

    public Long resolveComponentId(String appId) {
        return repo.findComponentIdByAppId(appId);
    }

    public List<Tool> findToolsByMappingType(Long componentId, String mappingType) {
        return repo.findToolsByMappingType(componentId, mappingType);
    }

    public List<Tool> findAllTools(Long componentId) {
        return repo.findAllTools(componentId);
    }


}
