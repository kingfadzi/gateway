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

    /** Fetch authoritative data for an appId from ServiceNow systems

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