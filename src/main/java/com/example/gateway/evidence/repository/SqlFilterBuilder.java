package com.example.gateway.evidence.repository;

import java.util.Map;

/**
 * Utility class for building SQL filter clauses and parameter maps.
 * Eliminates duplication across evidence repositories by providing common filter patterns.
 */
public class SqlFilterBuilder {

    /**
     * Add profile version filtering for KPI queries.
     * Ensures only the latest profile version for each app is included.
     *
     * @param sql StringBuilder to append SQL to
     * @param params Parameter map to add values to
     * @param appId Optional app ID filter (null = portfolio-wide)
     * @param profileAlias SQL alias for profile table (e.g., "p")
     */
    public static void addProfileVersionFilter(StringBuilder sql, Map<String, Object> params,
                                                String appId, String profileAlias) {
        if (appId != null && !appId.trim().isEmpty()) {
            sql.append(" AND ").append(profileAlias).append(".app_id = :appId");
            sql.append(" AND ").append(profileAlias).append(".version = (SELECT MAX(version) FROM profile WHERE app_id = :appId)");
            params.put("appId", appId.trim());
        } else {
            // Portfolio-wide: get latest version for each app
            sql.append(" AND ").append(profileAlias).append(".version = (SELECT MAX(version) FROM profile p2 WHERE p2.app_id = ")
               .append(profileAlias).append(".app_id)");
        }
    }

    /**
     * Add criticality filter supporting comma-separated values.
     *
     * @param sql StringBuilder to append SQL to
     * @param params Parameter map to add values to
     * @param criticality Comma-separated criticality values (e.g., "high,critical")
     * @param columnName Full column name (e.g., "app.app_criticality_assessment")
     */
    public static void addCriticalityFilter(StringBuilder sql, Map<String, Object> params,
                                            String criticality, String columnName) {
        if (criticality != null && !criticality.trim().isEmpty()) {
            String[] criticalityValues = criticality.split(",");
            sql.append(" AND ").append(columnName).append(" IN (");
            for (int i = 0; i < criticalityValues.length; i++) {
                sql.append(":criticality").append(i);
                if (i < criticalityValues.length - 1) {
                    sql.append(", ");
                }
                params.put("criticality" + i, criticalityValues[i].trim());
            }
            sql.append(")");
        }
    }

    /**
     * Add domain filter (derived_from field).
     *
     * @param sql StringBuilder to append SQL to
     * @param params Parameter map to add values to
     * @param domain Domain value (e.g., "security_rating")
     * @param columnName Full column name (e.g., "pf.derived_from")
     */
    public static void addDomainFilter(StringBuilder sql, Map<String, Object> params,
                                       String domain, String columnName) {
        if (domain != null && !domain.trim().isEmpty()) {
            sql.append(" AND ").append(columnName).append(" = :derivedFrom");
            params.put("derivedFrom", domain.trim());
        }
    }

    /**
     * Add field key filter.
     *
     * @param sql StringBuilder to append SQL to
     * @param params Parameter map to add values to
     * @param fieldKey Field key value (e.g., "bcdr_plan")
     * @param columnName Full column name (e.g., "pf.field_key")
     */
    public static void addFieldKeyFilter(StringBuilder sql, Map<String, Object> params,
                                         String fieldKey, String columnName) {
        if (fieldKey != null && !fieldKey.trim().isEmpty()) {
            sql.append(" AND ").append(columnName).append(" = :fieldKey");
            params.put("fieldKey", fieldKey.trim());
        }
    }

    /**
     * Add text search filter across multiple columns using ILIKE (case-insensitive).
     *
     * @param sql StringBuilder to append SQL to
     * @param params Parameter map to add values to
     * @param search Search term
     * @param searchColumns Columns to search (e.g., "e.uri", "d.title", "pf.field_key")
     */
    public static void addSearchFilter(StringBuilder sql, Map<String, Object> params,
                                       String search, String... searchColumns) {
        if (search != null && !search.trim().isEmpty()) {
            sql.append(" AND (");
            for (int i = 0; i < searchColumns.length; i++) {
                sql.append(searchColumns[i]).append(" ILIKE :search");
                if (i < searchColumns.length - 1) {
                    sql.append(" OR ");
                }
            }
            sql.append(")");
            params.put("search", "%" + search.trim() + "%");
        }
    }

    /**
     * Add pagination (LIMIT and OFFSET).
     *
     * @param sql StringBuilder to append SQL to
     * @param params Parameter map to add values to
     * @param limit Maximum number of results
     * @param offset Number of results to skip
     */
    public static void addPagination(StringBuilder sql, Map<String, Object> params,
                                     int limit, int offset) {
        sql.append(" LIMIT :limit OFFSET :offset");
        params.put("limit", limit);
        params.put("offset", offset);
    }

    /**
     * Add ORDER BY clause.
     *
     * @param sql StringBuilder to append SQL to
     * @param orderByClause Full ORDER BY clause (e.g., "e.created_at DESC")
     */
    public static void addOrderBy(StringBuilder sql, String orderByClause) {
        sql.append(" ORDER BY ").append(orderByClause);
    }

    /**
     * Add app ID filter.
     *
     * @param sql StringBuilder to append SQL to
     * @param params Parameter map to add values to
     * @param appId Application ID
     * @param columnName Full column name (e.g., "e.app_id")
     */
    public static void addAppIdFilter(StringBuilder sql, Map<String, Object> params,
                                      String appId, String columnName) {
        if (appId != null && !appId.trim().isEmpty()) {
            sql.append(" AND ").append(columnName).append(" = :appId");
            params.put("appId", appId.trim());
        }
    }

    /**
     * Add link status filter supporting comma-separated values.
     *
     * @param sql StringBuilder to append SQL to
     * @param params Parameter map to add values to
     * @param linkStatus Comma-separated link status values (e.g., "APPROVED,USER_ATTESTED")
     * @param columnName Full column name (e.g., "efl.link_status")
     */
    public static void addLinkStatusFilter(StringBuilder sql, Map<String, Object> params,
                                           String linkStatus, String columnName) {
        if (linkStatus != null && !linkStatus.trim().isEmpty()) {
            String[] statusValues = linkStatus.split(",");
            sql.append(" AND ").append(columnName).append(" IN (");
            for (int i = 0; i < statusValues.length; i++) {
                sql.append(":linkStatus").append(i);
                if (i < statusValues.length - 1) {
                    sql.append(", ");
                }
                params.put("linkStatus" + i, statusValues[i].trim());
            }
            sql.append(")");
        }
    }
}
