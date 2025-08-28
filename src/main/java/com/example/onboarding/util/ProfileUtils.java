package com.example.onboarding.util;

import com.example.onboarding.dto.application.ServiceInstanceRow;
import com.example.onboarding.dto.application.SourceRow;
import com.example.onboarding.dto.evidence.Evidence;
import com.example.onboarding.dto.profile.FieldRow;
import com.example.onboarding.dto.profile.ProfileField;
import com.example.onboarding.dto.profile.ProfileMeta;
import org.springframework.jdbc.core.RowMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public final class ProfileUtils {
    
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    private ProfileUtils() {
        // Utility class
    }
    
    public static OffsetDateTime odt(ResultSet rs, String col) throws SQLException {
        Timestamp ts = rs.getTimestamp(col);
        return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
    }
    
    public static Object jsonToJava(String json) {
        if (json == null) return null;
        try {
            JsonNode n = OBJECT_MAPPER.readTree(json);
            if (n.isTextual()) return n.textValue();
            if (n.isNumber())  return n.numberValue();
            if (n.isBoolean()) return n.booleanValue();
            if (n.isNull())    return null;
            return OBJECT_MAPPER.convertValue(n, Object.class);
        } catch (JsonProcessingException e) {
            return json; // fallback to raw string
        }
    }
    
    public static String toJsonString(Object v) {
        if (v == null) return "null";
        if (v instanceof Number || v instanceof Boolean) return v.toString();
        String s = v.toString();
        if (s.startsWith("{") || s.startsWith("[") || (s.startsWith("\"") && s.endsWith("\""))) return s;
        return "\"" + s.replace("\"", "\\\"") + "\"";
    }
    
    public static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(md.digest(bytes));
        } catch (Exception e) {
            throw new RuntimeException("Unable to compute sha256", e);
        }
    }
    
    private static String getNullableString(ResultSet rs, String col) throws SQLException {
        try {
            String v = rs.getString(col);
            return (v == null || rs.wasNull()) ? null : v;
        } catch (SQLException e) {
            return null;
        }
    }
    
    public static Evidence mapEvidence(ResultSet rs, int rowNum) throws SQLException {
        return new Evidence(
                rs.getString("evidence_id"),
                getNullableString(rs, "app_id"),
                rs.getString("profile_field_id"),
                getNullableString(rs, "claim_id"),
                rs.getString("uri"),
                rs.getString("type"),
                rs.getString("sha256"),
                rs.getString("source_system"),
                getNullableString(rs, "submitted_by"),
                rs.getObject("valid_from", java.time.OffsetDateTime.class),
                rs.getObject("valid_until", java.time.OffsetDateTime.class),
                rs.getString("status"),
                rs.getObject("revoked_at", java.time.OffsetDateTime.class),
                getNullableString(rs, "reviewed_by"),
                rs.getObject("reviewed_at", java.time.OffsetDateTime.class),
                getNullableString(rs, "related_evidence_fields"),
                getNullableString(rs, "track_id"),
                getNullableString(rs, "document_id"),
                getNullableString(rs, "doc_version_id"),
                rs.getObject("added_at", java.time.OffsetDateTime.class),
                rs.getObject("created_at", java.time.OffsetDateTime.class),
                rs.getObject("updated_at", java.time.OffsetDateTime.class)
        );
    }
    
    public static ProfileMeta mapProfileMeta(ResultSet rs, int rowNum) throws SQLException {
        return new ProfileMeta(
                rs.getString("profile_id"),
                rs.getInt("version"),
                odt(rs, "updated_at")
        );
    }
    
    public static ProfileField mapProfileField(ResultSet rs, int rowNum) throws SQLException {
        return new ProfileField(
                rs.getString("id"),
                rs.getString("field_key"),
                jsonToJava(rs.getString("value")),
                rs.getString("source_system"),
                rs.getString("source_ref"),
                rs.getInt("evidence_count"),
                odt(rs, "updated_at")
        );
    }
    
    
    public static ServiceInstanceRow mapServiceInstance(ResultSet rs, int rowNum) throws SQLException {
        return new ServiceInstanceRow(
                rs.getString("it_service_instance_sysid"),
                rs.getString("environment"),
                rs.getString("it_business_service_sysid"),
                rs.getString("business_application_sysid"),
                rs.getString("service_offering_join"),
                rs.getString("service_instance"),
                rs.getString("install_type"),
                rs.getString("service_classification")
        );
    }
    
    public static SourceRow mapSourceRow(ResultSet rs, int rowNum) throws SQLException {
        return new SourceRow(
                rs.getString("app_id"),
                rs.getString("business_service_name"),
                rs.getString("application_name"),

                rs.getString("application_type"),
                rs.getString("application_tier"),
                rs.getString("architecture_type"),
                rs.getString("install_type"),
                rs.getString("house_position"),
                rs.getString("operational_status"),

                rs.getString("transaction_cycle"),
                rs.getString("transaction_cycle_id"),

                rs.getString("application_product_owner"),
                rs.getString("application_product_owner_brid"),
                rs.getString("system_architect"),
                rs.getString("system_architect_brid"),

                rs.getString("business_application_sys_id"),
                rs.getString("application_parent"),
                rs.getString("application_parent_id"),
                rs.getString("architecture_hosting"),

                rs.getString("app_criticality"),
                rs.getString("security_rating"),
                rs.getString("confidentiality_rating"),
                rs.getString("integrity_rating"),
                rs.getString("availability_rating"),
                rs.getString("resilience_rating")
        );
    }
    
    
    public static final RowMapper<FieldRow> FIELD_ROW_MAPPER = new RowMapper<>() {
        @Override 
        public FieldRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new FieldRow(
                    rs.getString("field_id"),
                    rs.getString("field_key"),
                    rs.getString("value_json"),
                    rs.getString("source_system"),
                    rs.getString("source_ref"),
                    rs.getInt("evidence_count"),
                    odt(rs, "updated_at")
            );
        }
    };
}