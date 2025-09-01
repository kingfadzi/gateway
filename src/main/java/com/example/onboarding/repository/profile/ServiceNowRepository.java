package com.example.onboarding.repository.profile;

import com.example.onboarding.dto.application.SourceRow;
import com.example.onboarding.dto.application.ServiceInstanceRow;
import com.example.onboarding.util.ProfileUtils;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class ServiceNowRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public ServiceNowRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Fetch authoritative data for an appId from ServiceNow systems */
    public Optional<SourceRow> fetchApplicationData(String appId) {
        String sql = """
            SELECT
              child_app.correlation_id                     AS app_id,
              child_app.business_service_name              AS business_service_name,
              child_app.name                               AS application_name,
              child_app.application_type                   AS application_type,
              child_app.application_tier                   AS application_tier,
              child_app.architecture_type                  AS architecture_type,
              child_app.install_type                       AS install_type,
              child_app.house_position                     AS house_position,
              child_app.operational_status                 AS operational_status,
              child_app.transaction_cycle                  AS transaction_cycle,
              child_app.transaction_cycle_id               AS transaction_cycle_id,
              child_app.application_product_owner          AS application_product_owner,
              child_app.application_product_owner_brid     AS application_product_owner_brid,
              child_app.system_architect                   AS system_architect,
              child_app.system_architect_brid              AS system_architect_brid,
              child_app.business_application_sys_id        AS business_application_sys_id,
              child_app.application_parent                 AS application_parent,
              child_app.application_parent_correlation_id  AS application_parent_id,
              child_app.architecture_hosting               AS architecture_hosting,
              child_app.app_criticality_assessment         AS app_criticality,
              child_app.security_rating                    AS security_rating,
              child_app.confidentiality_rating             AS confidentiality_rating,
              child_app.integrity_rating                   AS integrity_rating,
              child_app.availability_rating                AS availability_rating,
              child_app.resilience_rating                  AS resilience_rating
            FROM public.spdw_vwsfbusinessapplication AS child_app
            WHERE child_app.correlation_id = :appId
            """;

        List<SourceRow> results = jdbc.query(sql, Map.of("appId", appId), ProfileUtils::mapSourceRow);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /** List all non-Dev service instances for the given appId from ServiceNow. */
    public List<ServiceInstanceRow> fetchServiceInstances(String appId) {
        String sql = """
            SELECT
              child_app.correlation_id           AS app_id,
              si.environment,
              si.it_business_service_sysid,
              si.business_application_sysid,
              si.service_offering_join,
              si.it_service_instance             AS service_instance,
              si.install_type,
              si.service_classification,
              si.it_service_instance_sysid
            FROM public.spdw_vwsfbusinessapplication AS child_app
            JOIN public.spdw_vwsfitserviceinstance  AS si
              ON child_app.business_application_sys_id = si.business_application_sysid
            WHERE child_app.correlation_id = :appId
              AND si.environment IS NOT NULL
              AND si.environment <> 'Dev'
            ORDER BY si.environment, si.it_service_instance
            """;

        return jdbc.query(sql, Map.of("appId", appId), ProfileUtils::mapServiceInstance);
    }
}