package com.example.gateway.evidence.repository;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SqlFilterBuilder utility class.
 * Tests all filter building methods to ensure correct SQL generation and parameter binding.
 */
class SqlFilterBuilderTest {

    @Test
    void shouldAddProfileVersionFilterWithAppId() {
        // Arrange
        StringBuilder sql = new StringBuilder();
        Map<String, Object> params = new HashMap<>();
        String appId = "app-123";
        String alias = "p";

        // Act
        SqlFilterBuilder.addProfileVersionFilter(sql, params, appId, alias);

        // Assert
        assertThat(sql.toString())
                .contains("AND p.app_id = :appId")
                .contains("AND p.version = (SELECT MAX(version) FROM profile WHERE app_id = :appId)");
        assertThat(params).containsEntry("appId", "app-123");
    }

    @Test
    void shouldAddProfileVersionFilterWithoutAppId() {
        // Arrange
        StringBuilder sql = new StringBuilder();
        Map<String, Object> params = new HashMap<>();
        String alias = "p";

        // Act
        SqlFilterBuilder.addProfileVersionFilter(sql, params, null, alias);

        // Assert
        assertThat(sql.toString())
                .contains("AND p.version = (SELECT MAX(version) FROM profile p2 WHERE p2.app_id = p.app_id)");
        assertThat(params).isEmpty();
    }

    @Test
    void shouldTrimAppIdInProfileVersionFilter() {
        // Arrange
        StringBuilder sql = new StringBuilder();
        Map<String, Object> params = new HashMap<>();

        // Act
        SqlFilterBuilder.addProfileVersionFilter(sql, params, "  app-123  ", "p");

        // Assert
        assertThat(params).containsEntry("appId", "app-123");
    }

    @Test
    void shouldAddCriticalityFilterWithSingleValue() {
        // Arrange
        StringBuilder sql = new StringBuilder();
        Map<String, Object> params = new HashMap<>();

        // Act
        SqlFilterBuilder.addCriticalityFilter(sql, params, "critical", "app.criticality");

        // Assert
        assertThat(sql.toString()).contains("AND app.criticality IN (:criticality0)");
        assertThat(params).containsEntry("criticality0", "critical");
    }

    @Test
    void shouldAddCriticalityFilterWithMultipleValues() {
        // Arrange
        StringBuilder sql = new StringBuilder();
        Map<String, Object> params = new HashMap<>();

        // Act
        SqlFilterBuilder.addCriticalityFilter(sql, params, "high,critical,medium", "app.criticality");

        // Assert
        assertThat(sql.toString())
                .contains("AND app.criticality IN (:criticality0, :criticality1, :criticality2)");
        assertThat(params)
                .containsEntry("criticality0", "high")
                .containsEntry("criticality1", "critical")
                .containsEntry("criticality2", "medium");
    }

    @Test
    void shouldNotAddCriticalityFilterWhenNull() {
        // Arrange
        StringBuilder sql = new StringBuilder();
        Map<String, Object> params = new HashMap<>();

        // Act
        SqlFilterBuilder.addCriticalityFilter(sql, params, null, "app.criticality");

        // Assert
        assertThat(sql.toString()).isEmpty();
        assertThat(params).isEmpty();
    }

    @Test
    void shouldNotAddCriticalityFilterWhenEmpty() {
        // Arrange
        StringBuilder sql = new StringBuilder();
        Map<String, Object> params = new HashMap<>();

        // Act
        SqlFilterBuilder.addCriticalityFilter(sql, params, "  ", "app.criticality");

        // Assert
        assertThat(sql.toString()).isEmpty();
        assertThat(params).isEmpty();
    }

    @Test
    void shouldAddDomainFilter() {
        // Arrange
        StringBuilder sql = new StringBuilder();
        Map<String, Object> params = new HashMap<>();

        // Act
        SqlFilterBuilder.addDomainFilter(sql, params, "security_rating", "pf.derived_from");

        // Assert
        assertThat(sql.toString()).contains("AND pf.derived_from = :derivedFrom");
        assertThat(params).containsEntry("derivedFrom", "security_rating");
    }

    @Test
    void shouldNotAddDomainFilterWhenNull() {
        // Arrange
        StringBuilder sql = new StringBuilder();
        Map<String, Object> params = new HashMap<>();

        // Act
        SqlFilterBuilder.addDomainFilter(sql, params, null, "pf.derived_from");

        // Assert
        assertThat(sql.toString()).isEmpty();
        assertThat(params).isEmpty();
    }

    @Test
    void shouldAddFieldKeyFilter() {
        // Arrange
        StringBuilder sql = new StringBuilder();
        Map<String, Object> params = new HashMap<>();

        // Act
        SqlFilterBuilder.addFieldKeyFilter(sql, params, "bcdr_plan", "pf.field_key");

        // Assert
        assertThat(sql.toString()).contains("AND pf.field_key = :fieldKey");
        assertThat(params).containsEntry("fieldKey", "bcdr_plan");
    }

    @Test
    void shouldNotAddFieldKeyFilterWhenNull() {
        // Arrange
        StringBuilder sql = new StringBuilder();
        Map<String, Object> params = new HashMap<>();

        // Act
        SqlFilterBuilder.addFieldKeyFilter(sql, params, null, "pf.field_key");

        // Assert
        assertThat(sql.toString()).isEmpty();
        assertThat(params).isEmpty();
    }

    @Test
    void shouldAddSearchFilterWithSingleColumn() {
        // Arrange
        StringBuilder sql = new StringBuilder();
        Map<String, Object> params = new HashMap<>();

        // Act
        SqlFilterBuilder.addSearchFilter(sql, params, "test", "e.uri");

        // Assert
        assertThat(sql.toString()).contains("AND (e.uri ILIKE :search)");
        assertThat(params).containsEntry("search", "%test%");
    }

    @Test
    void shouldAddSearchFilterWithMultipleColumns() {
        // Arrange
        StringBuilder sql = new StringBuilder();
        Map<String, Object> params = new HashMap<>();

        // Act
        SqlFilterBuilder.addSearchFilter(sql, params, "gitlab", "e.uri", "d.title", "pf.field_key");

        // Assert
        assertThat(sql.toString())
                .contains("AND (e.uri ILIKE :search OR d.title ILIKE :search OR pf.field_key ILIKE :search)");
        assertThat(params).containsEntry("search", "%gitlab%");
    }

    @Test
    void shouldNotAddSearchFilterWhenNull() {
        // Arrange
        StringBuilder sql = new StringBuilder();
        Map<String, Object> params = new HashMap<>();

        // Act
        SqlFilterBuilder.addSearchFilter(sql, params, null, "e.uri");

        // Assert
        assertThat(sql.toString()).isEmpty();
        assertThat(params).isEmpty();
    }

    @Test
    void shouldAddPagination() {
        // Arrange
        StringBuilder sql = new StringBuilder();
        Map<String, Object> params = new HashMap<>();

        // Act
        SqlFilterBuilder.addPagination(sql, params, 20, 40);

        // Assert
        assertThat(sql.toString()).contains("LIMIT :limit OFFSET :offset");
        assertThat(params)
                .containsEntry("limit", 20)
                .containsEntry("offset", 40);
    }

    @Test
    void shouldAddOrderBy() {
        // Arrange
        StringBuilder sql = new StringBuilder();

        // Act
        SqlFilterBuilder.addOrderBy(sql, "e.created_at DESC");

        // Assert
        assertThat(sql.toString()).isEqualTo(" ORDER BY e.created_at DESC");
    }

    @Test
    void shouldAddAppIdFilter() {
        // Arrange
        StringBuilder sql = new StringBuilder();
        Map<String, Object> params = new HashMap<>();

        // Act
        SqlFilterBuilder.addAppIdFilter(sql, params, "app-456", "e.app_id");

        // Assert
        assertThat(sql.toString()).contains("AND e.app_id = :appId");
        assertThat(params).containsEntry("appId", "app-456");
    }

    @Test
    void shouldNotAddAppIdFilterWhenNull() {
        // Arrange
        StringBuilder sql = new StringBuilder();
        Map<String, Object> params = new HashMap<>();

        // Act
        SqlFilterBuilder.addAppIdFilter(sql, params, null, "e.app_id");

        // Assert
        assertThat(sql.toString()).isEmpty();
        assertThat(params).isEmpty();
    }

    @Test
    void shouldAddLinkStatusFilterWithSingleValue() {
        // Arrange
        StringBuilder sql = new StringBuilder();
        Map<String, Object> params = new HashMap<>();

        // Act
        SqlFilterBuilder.addLinkStatusFilter(sql, params, "APPROVED", "efl.link_status");

        // Assert
        assertThat(sql.toString()).contains("AND efl.link_status IN (:linkStatus0)");
        assertThat(params).containsEntry("linkStatus0", "APPROVED");
    }

    @Test
    void shouldAddLinkStatusFilterWithMultipleValues() {
        // Arrange
        StringBuilder sql = new StringBuilder();
        Map<String, Object> params = new HashMap<>();

        // Act
        SqlFilterBuilder.addLinkStatusFilter(sql, params, "APPROVED,USER_ATTESTED,SUGGESTED", "efl.link_status");

        // Assert
        assertThat(sql.toString())
                .contains("AND efl.link_status IN (:linkStatus0, :linkStatus1, :linkStatus2)");
        assertThat(params)
                .containsEntry("linkStatus0", "APPROVED")
                .containsEntry("linkStatus1", "USER_ATTESTED")
                .containsEntry("linkStatus2", "SUGGESTED");
    }

    @Test
    void shouldNotAddLinkStatusFilterWhenNull() {
        // Arrange
        StringBuilder sql = new StringBuilder();
        Map<String, Object> params = new HashMap<>();

        // Act
        SqlFilterBuilder.addLinkStatusFilter(sql, params, null, "efl.link_status");

        // Assert
        assertThat(sql.toString()).isEmpty();
        assertThat(params).isEmpty();
    }

    @Test
    void shouldHandleComplexFilterCombination() {
        // Arrange
        StringBuilder sql = new StringBuilder("SELECT * FROM evidence e WHERE 1=1");
        Map<String, Object> params = new HashMap<>();

        // Act - Build a complex query with multiple filters
        SqlFilterBuilder.addAppIdFilter(sql, params, "app-789", "e.app_id");
        SqlFilterBuilder.addSearchFilter(sql, params, "security", "e.uri", "e.title");
        SqlFilterBuilder.addOrderBy(sql, "e.created_at DESC");
        SqlFilterBuilder.addPagination(sql, params, 10, 0);

        // Assert
        String result = sql.toString();
        assertThat(result)
                .contains("SELECT * FROM evidence e WHERE 1=1")
                .contains("AND e.app_id = :appId")
                .contains("AND (e.uri ILIKE :search OR e.title ILIKE :search)")
                .contains("ORDER BY e.created_at DESC")
                .contains("LIMIT :limit OFFSET :offset");

        assertThat(params)
                .containsEntry("appId", "app-789")
                .containsEntry("search", "%security%")
                .containsEntry("limit", 10)
                .containsEntry("offset", 0);
    }
}
