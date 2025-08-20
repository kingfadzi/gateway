package com.example.onboarding.application.repository;

import com.example.onboarding.application.dto.SourceRow;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
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
              child_app.correlation_id           AS app_id,
              bs.service                         AS business_service_name,
              child_app.application_type,
              child_app.application_tier,
              child_app.architecture_type,
              child_app.install_type,
              child_app.house_position,
              child_app.operational_status,
              child_app.owning_transaction_cycle AS transaction_cycle,

              so.app_criticality_assessment      AS app_criticality,
              so.security_rating                 AS security_rating,
              so.integrity_rating                AS integrity_rating,
              so.availability_rating             AS availability_rating,
              so.resiliency_category             AS resilience_rating
            FROM public.spdw_vwsfitbusinessservice bs
            JOIN public.lean_control_application lca
              ON lca.servicenow_app_id = bs.service_correlation_id
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
}
