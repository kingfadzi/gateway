package com.example.onboarding.repository.application;

import com.example.onboarding.dto.application.SourceRow;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Repository
public class ApplicationRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public ApplicationRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Upsert authoritative application facts from SourceRow (idempotent). */
    public void upsertFromSource(SourceRow src) {
        String sql = """
            INSERT INTO application (
              app_id, scope,
              parent_app_id, parent_app_name,
              name, business_service_name,
              app_criticality_assessment, security_rating, integrity_rating, availability_rating, resilience_rating,
              business_application_sys_id, architecture_hosting,
              jira_backlog_id, lean_control_service_id, repo_id,
              operational_status,
              transaction_cycle, transaction_cycle_id,
              application_type, application_tier, architecture_type, install_type, house_position,
              product_owner, product_owner_brid,
              system_architect, system_architect_brid,
              onboarding_status, owner_id, updated_at
            ) VALUES (
              :app_id, 'application',
              :parent_app_id, :parent_app_name,
              :name, :business_service_name,
              :app_criticality, :security_rating, :integrity_rating, :availability_rating, :resilience_rating,
              :business_application_sys_id, :architecture_hosting,
              :jira_backlog_id, :lean_control_service_id, :repo_id,
              :operational_status,
              :transaction_cycle, :transaction_cycle_id,
              :application_type, :application_tier, :architecture_type, :install_type, :house_position,
              :product_owner, :product_owner_brid,
              :system_architect, :system_architect_brid,
              COALESCE(:onboarding_status, 'pending'), :owner_id, :updated_at
            )
            ON CONFLICT (app_id) DO UPDATE SET
              parent_app_id              = COALESCE(EXCLUDED.parent_app_id,              application.parent_app_id),
              parent_app_name            = COALESCE(EXCLUDED.parent_app_name,            application.parent_app_name),
              name                       = COALESCE(EXCLUDED.name,                       application.name),
              business_service_name      = COALESCE(EXCLUDED.business_service_name,      application.business_service_name),
              app_criticality_assessment = COALESCE(EXCLUDED.app_criticality_assessment, application.app_criticality_assessment),
              security_rating            = COALESCE(EXCLUDED.security_rating,            application.security_rating),
              integrity_rating           = COALESCE(EXCLUDED.integrity_rating,           application.integrity_rating),
              availability_rating        = COALESCE(EXCLUDED.availability_rating,        application.availability_rating),
              resilience_rating          = COALESCE(EXCLUDED.resilience_rating,          application.resilience_rating),
              business_application_sys_id= COALESCE(EXCLUDED.business_application_sys_id,application.business_application_sys_id),
              architecture_hosting       = COALESCE(EXCLUDED.architecture_hosting,       application.architecture_hosting),
              jira_backlog_id            = COALESCE(EXCLUDED.jira_backlog_id,            application.jira_backlog_id),
              lean_control_service_id    = COALESCE(EXCLUDED.lean_control_service_id,    application.lean_control_service_id),
              repo_id                    = COALESCE(EXCLUDED.repo_id,                    application.repo_id),
              operational_status         = COALESCE(EXCLUDED.operational_status,         application.operational_status),
              transaction_cycle          = COALESCE(EXCLUDED.transaction_cycle,          application.transaction_cycle),
              transaction_cycle_id       = COALESCE(EXCLUDED.transaction_cycle_id,       application.transaction_cycle_id),
              application_type           = COALESCE(EXCLUDED.application_type,           application.application_type),
              application_tier           = COALESCE(EXCLUDED.application_tier,           application.application_tier),
              architecture_type          = COALESCE(EXCLUDED.architecture_type,          application.architecture_type),
              install_type               = COALESCE(EXCLUDED.install_type,               application.install_type),
              house_position             = COALESCE(EXCLUDED.house_position,             application.house_position),
              product_owner              = COALESCE(EXCLUDED.product_owner,              application.product_owner),
              product_owner_brid         = COALESCE(EXCLUDED.product_owner_brid,         application.product_owner_brid),
              system_architect           = COALESCE(EXCLUDED.system_architect,           application.system_architect),
              system_architect_brid      = COALESCE(EXCLUDED.system_architect_brid,      application.system_architect_brid),
              onboarding_status          = COALESCE(EXCLUDED.onboarding_status,          application.onboarding_status),
              owner_id                   = COALESCE(EXCLUDED.owner_id,                   application.owner_id),
              updated_at                 = EXCLUDED.updated_at
            """;

        var p = new MapSqlParameterSource()
            .addValue("app_id",                    src.appId())
            .addValue("parent_app_id",             nullIfBlank(src.applicationParentId()))
            .addValue("parent_app_name",           nullIfBlank(src.applicationParent()))
            .addValue("name",                      nullIfBlank(src.businessServiceName())) // using service name as display
            .addValue("business_service_name",     nullIfBlank(src.businessServiceName()))
            .addValue("app_criticality",           nullIfBlank(src.appCriticality()))
            .addValue("security_rating",           nullIfBlank(src.securityRating()))
            .addValue("integrity_rating",          nullIfBlank(src.integrityRating()))
            .addValue("availability_rating",       nullIfBlank(src.availabilityRating()))
            .addValue("resilience_rating",         nullIfBlank(src.resilienceRating()))
            .addValue("business_application_sys_id", nullIfBlank(src.businessApplicationSysId()))
            .addValue("architecture_hosting",      nullIfBlank(src.architectureHosting()))
            .addValue("jira_backlog_id",           null)   // not provided by source
            .addValue("lean_control_service_id",   null)   // not provided by source
            .addValue("repo_id",                   null)   // not provided by source
            .addValue("operational_status",        nullIfBlank(src.operationalStatus()))
            .addValue("transaction_cycle",         nullIfBlank(src.transactionCycle()))
            .addValue("transaction_cycle_id",      nullIfBlank(src.transactionCycleId()))
            .addValue("application_type",          nullIfBlank(src.applicationType()))
            .addValue("application_tier",          nullIfBlank(src.applicationTier()))
            .addValue("architecture_type",         nullIfBlank(src.architectureType()))
            .addValue("install_type",              nullIfBlank(src.installType()))
            .addValue("house_position",            nullIfBlank(src.housePosition()))
            .addValue("product_owner",             nullIfBlank(src.applicationProductOwner()))
            .addValue("product_owner_brid",        nullIfBlank(src.applicationProductOwnerBrid()))
            .addValue("system_architect",          nullIfBlank(src.systemArchitect()))
            .addValue("system_architect_brid",     nullIfBlank(src.systemArchitectBrid()))
            .addValue("onboarding_status",         null)   // keep existing unless you want to override
            .addValue("owner_id",                  null)   // not provided by source
            .addValue("updated_at",                OffsetDateTime.now(ZoneOffset.UTC));

        jdbc.update(sql, p);
    }

    private static String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
