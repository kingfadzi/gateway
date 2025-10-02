package com.example.gateway.application.mapper;

import com.example.gateway.application.dto.Application;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * RowMapper for Application entity.
 * Extracted to eliminate duplicate mapping logic and improve reusability.
 */
@Component
public class ApplicationRowMapper implements RowMapper<Application> {

    @Override
    public Application mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Application(
                rs.getString("app_id"),
                rs.getString("parent_app_id"),
                rs.getString("name"),
                rs.getString("app_criticality_assessment"),
                rs.getString("business_service_name"),
                rs.getString("jira_backlog_id"),
                rs.getString("lean_control_service_id"),
                rs.getString("repo_id"),
                rs.getString("operational_status"),
                rs.getString("transaction_cycle"),
                rs.getString("application_type"),
                rs.getString("application_tier"),
                rs.getString("architecture_type"),
                rs.getString("install_type"),
                rs.getString("house_position"),
                rs.getString("product_owner"),
                rs.getString("product_owner_brid"),
                rs.getString("onboarding_status"),
                rs.getString("owner_id"),
                rs.getString("security_rating"),
                rs.getString("confidentiality_rating"),
                rs.getString("integrity_rating"),
                rs.getString("availability_rating"),
                rs.getString("resilience_rating"),
                convertToOffsetDateTime(rs, "created_at"),
                convertToOffsetDateTime(rs, "updated_at"),
                (Boolean) rs.getObject("has_children")
        );
    }

    /**
     * Convert JDBC Timestamp to OffsetDateTime (UTC)
     */
    private static OffsetDateTime convertToOffsetDateTime(ResultSet rs, String columnName) throws SQLException {
        Timestamp ts = rs.getTimestamp(columnName);
        return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
    }
}
