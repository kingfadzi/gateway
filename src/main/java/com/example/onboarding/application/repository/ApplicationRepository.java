package com.example.onboarding.application.repository;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import static java.util.Map.entry;

@Repository
public class ApplicationRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public ApplicationRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Upsert minimal application facts we copy from the source fetch. */
    public void upsertFromSource(String appId, String businessServiceName,
                                 String appCriticality, String applicationType,
                                 String applicationTier, String architectureType,
                                 String installType, String housePosition,
                                 String operationalStatus, String transactionCycle) {
        String sql = """
            INSERT INTO application (
              app_id, scope, parent_app_id, name, business_service_name,
              app_criticality_assessment, jira_backlog_id, lean_control_service_id,
              repo_id, operational_status, transaction_cycle, application_type,
              application_tier, architecture_type, install_type, house_position,
              product_owner, product_owner_brid, onboarding_status, owner_id, updated_at
            ) VALUES (
              :app_id, 'application', NULL, NULL, :business_service_name,
              :app_criticality, NULL, NULL,
              NULL, :operational_status, :transaction_cycle, :application_type,
              :application_tier, :architecture_type, :install_type, :house_position,
              NULL, NULL, 'pending', NULL, :updated_at
            )
            ON CONFLICT (app_id) DO UPDATE SET
              business_service_name = EXCLUDED.business_service_name,
              app_criticality_assessment = EXCLUDED.app_criticality_assessment,
              operational_status = EXCLUDED.operational_status,
              transaction_cycle = EXCLUDED.transaction_cycle,
              application_type = EXCLUDED.application_type,
              application_tier = EXCLUDED.application_tier,
              architecture_type = EXCLUDED.architecture_type,
              install_type = EXCLUDED.install_type,
              house_position = EXCLUDED.house_position,
              updated_at = EXCLUDED.updated_at
            """;

        Map<String,Object> params = Map.ofEntries(
                entry("app_id", appId),
                entry("business_service_name", businessServiceName),
                entry("app_criticality", appCriticality),
                entry("operational_status", operationalStatus),
                entry("transaction_cycle", transactionCycle),
                entry("application_type", applicationType),
                entry("application_tier", applicationTier),
                entry("architecture_type", architectureType),
                entry("install_type", installType),
                entry("house_position", housePosition),
                entry("updated_at", java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC))
        );
        jdbc.update(sql, params);

    }
}
