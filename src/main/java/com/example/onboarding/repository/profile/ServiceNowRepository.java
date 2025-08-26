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

    /** Fetch authoritative data for an appId from ServiceNow systems. */
    public Optional<SourceRow> fetchApplicationData(String appId) {
        String sql = """
            SELECT
              child_app.correlation_id                    AS app_id,
              bs.service                                  AS business_service_name,
              child_app.business_application_name         AS application_name,

              child_app.application_type,
              child_app.applica, apption_tier,
              child_app.architecture_type,
              child_app.install_type,
              child_app.house_position,
              child_app.operational_status,
              child_app.owning_transaction_cycle          AS transaction_cycle,

              child_app.owning_transaction_cycle_id       AS transaction_cycle_id,

              child_app.application_product_owner,
              child_app.application_product_owner_brid,
              child_app.system_architect,
              child_app.system_architect_brid,

              child_app.business_application_sys_id,
              child_app.application_parent,
              child_app.application_parent_correlation_id AS application_parent_id,
              child_app.architecture_hosting,

              so.app_criticality_assessment               AS app_criticality,
              so.security_rating                           AS security_rating,
              so.confidentiality_rating                    AS confidentiality_rating,
              so.integrity_rating                          AS integrity_rating,
              so.availability_rating                       AS availability_rating,
              so.resiliency_category                       AS resilience_rating

            FROM public.spdw_vwsfitbusinessservice bs
            JOIN public.spdw_vwsfitserviceinstance si
              ON bs.it_business_service_sysid = si.it_business_service_sysid
            JOIN public.spdw_vwsfbusinessapplication child_app
              ON si.business_application_sysid = child_app.business_application_sys_id
            JOIN public.spdw_vwsfserviceoffering so
              ON so.service_offering_join = si.service_offering_join

            WHERE child_app.correlation_id = :appId
            LIMIT 1
            """;

        var rows = jdbc.query(sql, Map.of("appId", appId), ProfileUtils::mapSourceRow);
        return rows.stream().findFirst();
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