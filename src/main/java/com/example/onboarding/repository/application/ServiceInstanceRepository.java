package com.example.onboarding.repository.application;

import com.example.onboarding.dto.application.ServiceInstanceRow;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Repository
public class ServiceInstanceRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public ServiceInstanceRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Upsert all service instances for an app. Idempotent; keyed on it_service_instance_sysid. */
    public void upsertAll(String appId, List<ServiceInstanceRow> rows) {
        if (rows == null || rows.isEmpty()) return;

        String sql = """
            INSERT INTO service_instances (
                it_service_instance_sysid,
                app_id,
                environment,
                it_business_service_sysid,
                business_application_sysid,
                service_offering_join,
                service_instance,
                install_type,
                service_classification,
                updated_at
            ) VALUES (
                :pk,
                :app_id,
                :environment,
                :it_bs_sysid,
                :ba_sysid,
                :svc_off_join,
                :svc_instance,
                :install_type,
                :svc_classification,
                :updated_at
            )
            ON CONFLICT (it_service_instance_sysid) DO UPDATE SET
                app_id                     = EXCLUDED.app_id,
                environment                = EXCLUDED.environment,
                it_business_service_sysid  = EXCLUDED.it_business_service_sysid,
                business_application_sysid = EXCLUDED.business_application_sysid,
                service_offering_join      = EXCLUDED.service_offering_join,
                service_instance           = EXCLUDED.service_instance,
                install_type               = EXCLUDED.install_type,
                service_classification     = EXCLUDED.service_classification,
                updated_at                 = EXCLUDED.updated_at
            """;

        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var batch = rows.stream().map(r ->
            new MapSqlParameterSource()
                .addValue("pk", r.itServiceInstanceSysid())
                .addValue("app_id", appId)
                .addValue("environment", r.environment())
                .addValue("it_bs_sysid", r.itBusinessServiceSysid())
                .addValue("ba_sysid", r.businessApplicationSysid())
                .addValue("svc_off_join", r.serviceOfferingJoin())
                .addValue("svc_instance", r.serviceInstance())
                .addValue("install_type", r.installType())
                .addValue("svc_classification", r.serviceClassification())
                .addValue("updated_at", now)
        ).toArray(MapSqlParameterSource[]::new);

        jdbc.batchUpdate(sql, batch);
    }
}
