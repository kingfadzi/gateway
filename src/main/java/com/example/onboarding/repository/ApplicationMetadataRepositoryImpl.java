package com.example.onboarding.repository;

import com.example.onboarding.model.ApplicationMetadata;
import com.example.onboarding.model.EnvironmentInstance;
import com.example.onboarding.util.SqlLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Collections;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class ApplicationMetadataRepositoryImpl implements ApplicationMetadataRepository {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationMetadataRepositoryImpl.class);

    private final JdbcTemplate jdbc;
    private final String appMetaSql = SqlLoader.load("app_metadata.sql");
    private final String appChildrenSql = SqlLoader.load("app_children.sql");
    private final String appEnvironmentsSql = SqlLoader.load("app_environments.sql");

    public ApplicationMetadataRepositoryImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ApplicationMetadata> findByAppId(String appId) {
        try {
            ApplicationMetadata parent = jdbc.query(appMetaSql, rs -> {
                if (rs.next()) {
                    return mapRow(rs);
                }
                return null;
            }, appId);

            if (parent == null) {
                logger.debug("findByAppId({}): No ApplicationMetadata found, returning Optional.empty()", appId);
                return Optional.empty();
            }

            List<ApplicationMetadata> children = findChildren(appId);
            parent.setChildren(children);
            logger.debug("findByAppId({}): Returning parent {} with {} children", appId, parent, children.size());
            return Optional.of(parent);

        } catch (Exception e) {
            logger.error("Error retrieving ApplicationMetadata for appId={}", appId, e);
            logger.debug("findByAppId({}): Exception occurred, returning Optional.empty()", appId);
            return Optional.empty();
        }
    }

    @Override
    public List<ApplicationMetadata> findChildren(String parentAppId) {
        try {
            List<ApplicationMetadata> children = jdbc.query(appChildrenSql, (rs, rowNum) -> mapRow(rs), parentAppId);
            logger.debug("findChildren({}): Returning {} children: {}", parentAppId, children.size(), children);
            return children;
        } catch (Exception e) {
            logger.error("Error retrieving children for parentAppId={}", parentAppId, e);
            logger.debug("findChildren({}): Exception occurred, returning empty list", parentAppId);
            return Collections.emptyList();
        }
    }

    public List<EnvironmentInstance> findEnvironmentsByAppId(String appId) {
        try {
            List<EnvironmentInstance> instances = jdbc.query(appEnvironmentsSql, (rs, rowNum) -> mapEnvironment(rs), appId);
            logger.debug("findEnvironmentsByAppId({}): Found {} instances", appId, instances.size());
            return instances;
        } catch (Exception e) {
            logger.error("Error retrieving environments for appId={}", appId, e);
            return Collections.emptyList();
        }
    }

    private EnvironmentInstance mapEnvironment(ResultSet rs) throws SQLException {
        EnvironmentInstance ei = new EnvironmentInstance();
        ei.setServiceCorrelationId(rs.getString("service_correlation_id"));
        ei.setServiceName(rs.getString("service"));
        ei.setAppCorrelationId(rs.getString("correlation_id"));
        ei.setAppName(rs.getString("business_application_name"));
        ei.setInstanceCorrelationId(rs.getString("instance_correlation_id"));
        ei.setInstanceName(rs.getString("it_service_instance"));
        ei.setEnvironment(rs.getString("environment"));
        ei.setInstallType(rs.getString("install_type"));
        return ei;
    }


    private ApplicationMetadata mapRow(ResultSet rs) throws SQLException {
        ApplicationMetadata meta = new ApplicationMetadata();
        meta.setAppId(rs.getString("app_id"));
        meta.setAppName(rs.getString("app_name"));
        meta.setActive(rs.getBoolean("active"));
        meta.setOwningTransactionCycle(rs.getString("owning_transaction_cycle"));
        meta.setOwningTransactionCycleId(rs.getString("owning_transaction_cycle_id"));
        meta.setResilienceCategory(rs.getString("resilience_category"));
        meta.setOperationalStatus(rs.getString("operational_status"));
        meta.setApplicationType(rs.getString("application_type"));
        meta.setArchitectureType(rs.getString("architecture_type"));
        meta.setInstallType(rs.getString("install_type"));
        meta.setApplicationParent(rs.getString("application_parent"));
        meta.setApplicationParentCorrelationId(rs.getString("application_parent_correlation_id"));
        meta.setHousePosition(rs.getString("house_position"));
        //meta.setCeaseDate(rs.getObject("cease_date", LocalDate.class));
        meta.setBusinessApplicationSysId(rs.getString("business_application_sys_id"));
        meta.setApplicationTier(rs.getString("application_tier"));
        meta.setApplicationProductOwner(rs.getString("application_product_owner"));
        meta.setSystemArchitect(rs.getString("system_architect"));
        return meta;
    }
}
