package com.example.onboarding.repository;

import com.example.onboarding.model.Tool;
import com.example.onboarding.model.ToolMappings;
import com.example.onboarding.util.SqlLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class ComponentMappingRepositoryImpl implements ComponentMappingRepository {

    private final JdbcTemplate jdbc;
    private final String mappingSql = SqlLoader.load("component_mappings.sql");

    public ComponentMappingRepositoryImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<Tool> toolRowMapper = (rs, rowNum) -> {
        Tool tool = new Tool();
        tool.setComponentId(rs.getString("component_id"));
        tool.setComponentName(rs.getString("component_name"));
        tool.setTransactionCycle(rs.getString("transaction_cycle"));
        tool.setMappingType(rs.getString("mapping_type"));
        tool.setInstanceUrl(rs.getString("instance_url"));
        tool.setToolType(rs.getString("tool_type"));
        tool.setToolElementId(rs.getString("tool_element_id"));
        tool.setName(rs.getString("name"));
        tool.setIdentifier(rs.getString("identifier"));
        tool.setWebUrl(rs.getString("web_url"));
        return tool;
    };

    @Override
    public ToolMappings resolveMappings(String componentId) {
        List<Tool> allTools = jdbc.query(mappingSql, toolRowMapper, componentId);

        ToolMappings mappings = new ToolMappings();
        mappings.setVersionControlTools(filterByType(allTools, "version_control"));
        mappings.setWorkManagementTools(filterByType(allTools, "work_management"));
        mappings.setServiceManagementTools(filterByType(allTools, "service_management"));
        return mappings;
    }

    @Override
    public List<Tool> findByComponentIdAndToolType(String componentId, String toolType) {
        String sql = mappingSql + " AND tool_type = ?";
        return jdbc.query(sql, toolRowMapper, componentId, toolType);
    }

    private List<Tool> filterByType(List<Tool> tools, String type) {
        List<Tool> filtered = new ArrayList<>();
        for (Tool tool : tools) {
            if (type.equals(tool.getMappingType())) {
                filtered.add(tool);
            }
        }
        return filtered;
    }

    // Implementation
    @Override
    public Long findComponentIdByAppId(String appId) {
        try {
            return jdbc.queryForObject(SqlLoader.load("component_id.sql"), Long.class, appId);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public List<Tool> findToolsByMappingType(Long componentId, String mappingType) {
        return jdbc.query(SqlLoader.load("component_mappings.sql"), toolRowMapper, componentId)
                .stream()
                .filter(tool -> mappingType.equals(tool.getMappingType()))
                .toList();
    }

    @Override
    public List<Tool> findAllTools(Long componentId) {
        return jdbc.query(mappingSql, toolRowMapper, componentId);
    }


}
