package com.example.onboarding.repository.application;

import com.example.onboarding.dto.application.SourceRow;
import com.example.onboarding.dto.application.ServiceInstanceRow;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class SourceDao {
    private final NamedParameterJdbcTemplate jdbc;

    public SourceDao(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Fetch authoritative data for an appId from source systems. */
    public Optional<SourceRow> fetchByAppId(String appId) {
        String sql = """
            SELECT
              child_app.correlation_id                    AS app_id,
              bs.service                                  AS business_service_name,

              child_app.application_type,
              child_app.application_tier,
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

        var rows = jdbc.query(sql, Map.of("appId", appId), new RowMapper<SourceRow>() {
            @Override public SourceRow mapRow(ResultSet rs, int rowNum) throws SQLException {
                return new SourceRow(
                        rs.getString("app_id"),
                        rs.getString("business_service_name"),

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
                        rs.getString("integrity_rating"),
                        rs.getString("availability_rating"),
                        rs.getString("resilience_rating")
                );
            }
        });
        return rows.stream().findFirst();
    }

    /** List all non-Dev service instances for the given appId. */
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

        return jdbc.query(sql, Map.of("appId", appId), new RowMapper<ServiceInstanceRow>() {
            @Override public ServiceInstanceRow mapRow(ResultSet rs, int rowNum) throws SQLException {
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
        });
    }
}
