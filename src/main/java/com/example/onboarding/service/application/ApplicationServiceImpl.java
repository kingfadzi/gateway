package com.example.onboarding.service.application;

import com.example.onboarding.dto.*;
import com.example.onboarding.service.ApplicationService;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class ApplicationServiceImpl implements ApplicationService {

    private final NamedParameterJdbcTemplate jdbc;

    public ApplicationServiceImpl(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /* -------- time util: convert JDBC Timestamp -> OffsetDateTime(UTC) -------- */
    private static OffsetDateTime odt(ResultSet rs, String col) throws SQLException {
        Timestamp ts = rs.getTimestamp(col);
        return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
    }

    private static final RowMapper<ApplicationDto> APP_MAPPER = new RowMapper<>() {
        @Override public ApplicationDto mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new ApplicationDto(
                    rs.getString("app_id"),
                    rs.getString("parent_app_id"),
                    rs.getString("name"),
                    rs.getString("app_criticality_assessment"),
                    rs.getString("business_service_name"), // NEW
                    rs.getString("jira_backlog_id"),
                    rs.getString("lean_control_service_id"),
                    rs.getString("repo_id"),
                    rs.getString("operational_status"),
                    rs.getString("transaction_cycle"),
                    rs.getString("application_type"),
                    rs.getString("application_tier"),
                    rs.getString("architecture_type"),
                    rs.getString("install_type"),
                    rs.getString("house_position"),
                    rs.getString("product_owner"),
                    rs.getString("product_owner_brid"),
                    rs.getString("onboarding_status"),
                    rs.getString("owner_id"),
                    odt(rs, "created_at"),
                    odt(rs, "updated_at"),
                    (Boolean) rs.getObject("has_children")
            );
        }
    };

    @Override
    public PageResponse<ApplicationDto> list(String q, String ownerId, String onboardingStatus, String operationalStatus,
                                             String parentAppId, String sort, int page, int pageSize) {

        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        Map<String,Object> params = new HashMap<>();
        if (q != null && !q.isBlank()) {
            // Keep existing behavior (name only). If you want to search by business_service_name too, add: OR LOWER(a.business_service_name) LIKE :q
            where.append(" AND LOWER(a.name) LIKE :q ");
            params.put("q", "%" + q.toLowerCase() + "%");
        }
        if (ownerId != null && !ownerId.isBlank()) {
            where.append(" AND a.owner_id = :ownerId ");
            params.put("ownerId", ownerId);
        }
        if (onboardingStatus != null && !onboardingStatus.isBlank()) {
            where.append(" AND a.onboarding_status = :onb ");
            params.put("onb", onboardingStatus);
        }
        if (operationalStatus != null && !operationalStatus.isBlank()) {
            where.append(" AND a.operational_status = :ops ");
            params.put("ops", operationalStatus);
        }
        if (parentAppId != null && !parentAppId.isBlank()) {
            where.append(" AND a.parent_app_id = :parentId ");
            params.put("parentId", parentAppId);
        }

        String orderBy = switch (sort == null ? "" : sort) {
            case "name" -> " ORDER BY a.name ASC ";
            case "-name" -> " ORDER BY a.name DESC ";
            case "-updated_at" -> " ORDER BY a.updated_at DESC ";
            case "updated_at" -> " ORDER BY a.updated_at ASC ";
            default -> " ORDER BY a.updated_at DESC ";
        };

        String countSql = "SELECT COUNT(*) FROM application a " + where;
        long total = jdbc.queryForObject(countSql, params, Long.class);

        int offset = (Math.max(page,1)-1) * Math.max(pageSize,1);
        params.put("limit", pageSize);
        params.put("offset", offset);

        String sql = """
            SELECT a.*,
                   EXISTS (SELECT 1 FROM application c WHERE c.parent_app_id = a.app_id) AS has_children
            FROM application a
        """ + where + orderBy + " LIMIT :limit OFFSET :offset";

        List<ApplicationDto> items = jdbc.query(sql, new MapSqlParameterSource(params), APP_MAPPER);
        return new PageResponse<>(Math.max(page,1), Math.max(pageSize,1), total, items);
    }

    @Override
    public ApplicationDto get(String appId) {
        String sql = """
          SELECT a.*,
                 EXISTS (SELECT 1 FROM application c WHERE c.parent_app_id = a.app_id) AS has_children
          FROM application a
          WHERE a.app_id = :id
        """;
        try {
            return jdbc.queryForObject(sql, Map.of("id", appId), APP_MAPPER);
        } catch (EmptyResultDataAccessException ex) {
            throw notFound("Application not found: " + appId);
        }
    }

    @Override
    @Transactional
    public ApplicationDto create(CreateAppRequest req) {
        String appId = "app_" + UUID.randomUUID().toString().replace("-", "");
        String sql = """
            INSERT INTO application (
              app_id, parent_app_id, name, business_service_name, app_criticality_assessment,
              jira_backlog_id, lean_control_service_id, repo_id,
              operational_status, transaction_cycle, application_type, application_tier,
              architecture_type, install_type, house_position, product_owner, product_owner_brid,
              onboarding_status, owner_id, created_at, updated_at
            ) VALUES (
              :app_id, :parent, :name, :bs_name, :crit,
              :jira, :lcs, :repo,
              :ops, :txc, :apptype, :apptier,
              :arch, :inst, :house, :po, :pobrid,
              :onb, :owner, now(), now()
            )
        """;
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("app_id", appId)
                .addValue("parent", req.parentAppId())
                .addValue("name", req.name())
                .addValue("bs_name", req.businessServiceName()) // NEW
                .addValue("crit", req.appCriticalityAssessment())
                .addValue("jira", req.jiraBacklogId())
                .addValue("lcs", req.leanControlServiceId())
                .addValue("repo", req.repoId())
                .addValue("ops", req.operationalStatus())
                .addValue("txc", req.transactionCycle())
                .addValue("apptype", req.applicationType())
                .addValue("apptier", req.applicationTier())
                .addValue("arch", req.architectureType())
                .addValue("inst", req.installType())
                .addValue("house", req.housePosition())
                .addValue("po", req.productOwner())
                .addValue("pobrid", req.productOwnerBrid())
                .addValue("onb", req.onboardingStatus())
                .addValue("owner", req.ownerId());

        jdbc.update(sql, p);
        return get(appId);
    }

    @Override
    @Transactional
    public ApplicationDto patch(String appId, UpdateAppRequest req) {
        // Optimistic concurrency
        if (req.expectedUpdatedAt() != null) {
            int matched = jdbc.update("""
                UPDATE application
                SET updated_at = updated_at
                WHERE app_id = :id AND updated_at = :exp
            """, new MapSqlParameterSource()
                    .addValue("id", appId)
                    .addValue("exp", req.expectedUpdatedAt().toInstant()));
            if (matched == 0) {
                throw conflict("Update conflict – the record was modified by someone else.");
            }
        }

        StringBuilder set = new StringBuilder();
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("id", appId);
        setField(set, p, "parent_app_id", req.parentAppId());
        setField(set, p, "name", req.name());
        setField(set, p, "business_service_name", req.businessServiceName()); // NEW
        setField(set, p, "app_criticality_assessment", req.appCriticalityAssessment());
        setField(set, p, "jira_backlog_id", req.jiraBacklogId());
        setField(set, p, "lean_control_service_id", req.leanControlServiceId());
        setField(set, p, "repo_id", req.repoId());
        setField(set, p, "operational_status", req.operationalStatus());
        setField(set, p, "transaction_cycle", req.transactionCycle());
        setField(set, p, "application_type", req.applicationType());
        setField(set, p, "application_tier", req.applicationTier());
        setField(set, p, "architecture_type", req.architectureType());
        setField(set, p, "install_type", req.installType());
        setField(set, p, "house_position", req.housePosition());
        setField(set, p, "product_owner", req.productOwner());
        setField(set, p, "product_owner_brid", req.productOwnerBrid());
        setField(set, p, "onboarding_status", req.onboardingStatus());
        setField(set, p, "owner_id", req.ownerId());

        if (set.length() == 0) return get(appId);

        String sql = "UPDATE application SET " + set + ", updated_at = now() WHERE app_id = :id";
        int rows = jdbc.update(sql, p);
        if (rows == 0) throw notFound("Application not found: " + appId);
        return get(appId);
    }

    @Override
    @Transactional
    public void delete(String appId, boolean soft) {
        if (soft) {
            int n = jdbc.update("""
              UPDATE application SET onboarding_status = 'archived', updated_at = now()
              WHERE app_id = :id
            """, Map.of("id", appId));
            if (n == 0) throw notFound("Application not found: " + appId);
        } else {
            int n = jdbc.update("DELETE FROM application WHERE app_id = :id", Map.of("id", appId));
            if (n == 0) throw notFound("Application not found: " + appId);
        }
    }

    private static void setField(StringBuilder set, MapSqlParameterSource p, String col, Object val) {
        if (val != null) {
            if (set.length() > 0) set.append(", ");
            set.append(col).append(" = :").append(col);
            p.addValue(col, val);
        }
    }

    private static RuntimeException notFound(String msg) {
        return new NoSuchElementException(msg);
    }
    private static RuntimeException conflict(String msg) {
        return new IllegalStateException(msg);
    }
}
