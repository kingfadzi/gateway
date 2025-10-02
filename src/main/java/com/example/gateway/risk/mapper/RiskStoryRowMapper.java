package com.example.gateway.risk.mapper;

import com.example.gateway.risk.dto.RiskStoryResponse;
import com.example.gateway.risk.model.RiskCreationType;
import com.example.gateway.risk.model.RiskStatus;
import com.example.gateway.risk.model.RiskStory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Utility class for mapping database rows to RiskStory domain objects.
 * Extracted from RiskStoryServiceImpl to eliminate duplication and improve maintainability.
 */
@Component
public class RiskStoryRowMapper {

    private static final Logger log = LoggerFactory.getLogger(RiskStoryRowMapper.class);

    private final ObjectMapper objectMapper;

    public RiskStoryRowMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Map a database row to RiskStoryResponse with application details
     */
    public RiskStoryResponse mapToRiskStoryResponse(Map<String, Object> row) {
        RiskStory riskStory = mapToRiskStory(row);
        RiskStoryResponse.ApplicationDetails applicationDetails = mapToApplicationDetails(row);
        return RiskStoryResponse.fromModel(riskStory, applicationDetails);
    }

    /**
     * Map database row to RiskStory domain object
     */
    public RiskStory mapToRiskStory(Map<String, Object> row) {
        RiskStory risk = new RiskStory();
        risk.setRiskId((String) row.get("risk_id"));
        risk.setAppId((String) row.get("app_id"));
        risk.setFieldKey((String) row.get("field_key"));
        risk.setProfileId((String) row.get("profile_id"));
        risk.setProfileFieldId((String) row.get("profile_field_id"));
        risk.setTrackId((String) row.get("track_id"));
        risk.setTriggeringEvidenceId((String) row.get("triggering_evidence_id"));
        risk.setCreationType(row.get("creation_type") != null ?
            RiskCreationType.valueOf((String) row.get("creation_type")) : null);
        risk.setAssignedSme((String) row.get("assigned_sme"));
        risk.setTitle((String) row.get("title"));
        risk.setHypothesis((String) row.get("hypothesis"));
        risk.setCondition((String) row.get("condition"));
        risk.setConsequence((String) row.get("consequence"));
        risk.setControlRefs((String) row.get("control_refs"));
        risk.setSeverity((String) row.get("severity"));
        risk.setStatus(row.get("status") != null ?
            RiskStatus.valueOf((String) row.get("status")) : null);
        risk.setClosureReason((String) row.get("closure_reason"));
        risk.setRaisedBy((String) row.get("raised_by"));
        risk.setOwner((String) row.get("owner"));
        risk.setReviewComment((String) row.get("review_comment"));

        // Handle JSONB fields
        risk.setAttributes(parseJsonColumn(row.get("attributes")));
        risk.setPolicyRequirementSnapshot(parseJsonColumn(row.get("policy_requirement_snapshot")));

        // Handle timestamps
        risk.setOpenedAt(convertToOffsetDateTime(row.get("opened_at")));
        risk.setClosedAt(convertToOffsetDateTime(row.get("closed_at")));
        risk.setAssignedAt(convertToOffsetDateTime(row.get("assigned_at")));
        risk.setReviewedAt(convertToOffsetDateTime(row.get("reviewed_at")));
        risk.setCreatedAt(convertToOffsetDateTime(row.get("created_at")));
        risk.setUpdatedAt(convertToOffsetDateTime(row.get("updated_at")));

        return risk;
    }

    /**
     * Map database row to ApplicationDetails
     */
    public RiskStoryResponse.ApplicationDetails mapToApplicationDetails(Map<String, Object> row) {
        return new RiskStoryResponse.ApplicationDetails(
            (String) row.get("name"),
            (String) row.get("scope"),
            (String) row.get("parent_app_id"),
            (String) row.get("parent_app_name"),
            (String) row.get("business_service_name"),
            (String) row.get("app_criticality_assessment"),
            (String) row.get("security_rating"),
            (String) row.get("confidentiality_rating"),
            (String) row.get("integrity_rating"),
            (String) row.get("availability_rating"),
            (String) row.get("resilience_rating"),
            (String) row.get("business_application_sys_id"),
            (String) row.get("architecture_hosting"),
            (String) row.get("jira_backlog_id"),
            (String) row.get("lean_control_service_id"),
            (String) row.get("repo_id"),
            (String) row.get("operational_status"),
            (String) row.get("transaction_cycle"),
            (String) row.get("transaction_cycle_id"),
            (String) row.get("application_type"),
            (String) row.get("application_tier"),
            (String) row.get("architecture_type"),
            (String) row.get("install_type"),
            (String) row.get("house_position"),
            (String) row.get("product_owner"),
            (String) row.get("product_owner_brid"),
            (String) row.get("system_architect"),
            (String) row.get("system_architect_brid"),
            (String) row.get("onboarding_status"),
            (String) row.get("owner_id"),
            convertToOffsetDateTime(row.get("app_created_at")),
            convertToOffsetDateTime(row.get("app_updated_at"))
        );
    }

    /**
     * Convert various timestamp types to OffsetDateTime
     */
    public OffsetDateTime convertToOffsetDateTime(Object timestamp) {
        if (timestamp == null) {
            return null;
        }

        if (timestamp instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) timestamp).toInstant().atOffset(java.time.ZoneOffset.UTC);
        }

        if (timestamp instanceof java.time.Instant) {
            return ((java.time.Instant) timestamp).atOffset(java.time.ZoneOffset.UTC);
        }

        if (timestamp instanceof java.time.OffsetDateTime) {
            return (OffsetDateTime) timestamp;
        }

        if (timestamp instanceof java.time.LocalDateTime) {
            return ((java.time.LocalDateTime) timestamp).atOffset(java.time.ZoneOffset.UTC);
        }

        // If we can't convert, log and return null
        log.warn("Unable to convert timestamp type: {} value: {}", timestamp.getClass().getName(), timestamp);
        return null;
    }

    /**
     * Parse JSONB column from database result
     */
    public Map<String, Object> parseJsonColumn(Object jsonObj) {
        if (jsonObj == null) {
            return new java.util.HashMap<>();
        }

        if (jsonObj instanceof String jsonString) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = objectMapper.readValue(jsonString, Map.class);
                return parsed;
            } catch (Exception e) {
                log.warn("Failed to parse JSON column: {}", e.getMessage());
                return new java.util.HashMap<>();
            }
        }

        if (jsonObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) jsonObj;
            return new java.util.HashMap<>(map);
        }

        // Handle PostgreSQL PGobject
        if (jsonObj.getClass().getName().contains("PGobject")) {
            try {
                String jsonString = jsonObj.toString();
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = objectMapper.readValue(jsonString, Map.class);
                return parsed;
            } catch (Exception e) {
                log.warn("Failed to parse PGobject JSON: {}", e.getMessage());
                return new java.util.HashMap<>();
            }
        }

        log.warn("Unknown JSON column type: {}", jsonObj.getClass());
        return new java.util.HashMap<>();
    }

    /**
     * Convert domain filter to derivedFrom field name
     */
    public static String convertDomainToDerivedFrom(String domain, String derivedFrom) {
        if (domain != null && derivedFrom == null) {
            return domain + "_rating";
        }
        return derivedFrom;
    }
}
