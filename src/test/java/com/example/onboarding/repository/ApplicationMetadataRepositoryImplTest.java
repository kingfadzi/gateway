package com.example.onboarding.repository;

import com.example.onboarding.model.ApplicationMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApplicationMetadataRepositoryImplTest {

    private JdbcTemplate jdbcTemplate;
    private ApplicationMetadataRepositoryImpl repo;

    @BeforeEach
    void setup() {
        jdbcTemplate = mock(JdbcTemplate.class);
        repo = new ApplicationMetadataRepositoryImpl(jdbcTemplate);
    }

    @Test
    void findByAppId_shouldReturnAppWithChildren() {
        String appId = "APP123042";
        String componentId = "COMP001";

        ApplicationMetadata parent = new ApplicationMetadata();
        parent.setAppId(appId);
        parent.setAppName("Jumpstart App");
        parent.setComponentId(componentId);

        ApplicationMetadata child = new ApplicationMetadata();
        child.setAppId("APP321001");
        child.setAppName("Auth Service");

        // Mock component ID fetch
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), eq(appId)))
                .thenReturn(componentId);

        // Mock parent app fetch (query returns single object)
        when(jdbcTemplate.query(eq("app_metadata.sql content"), any(ResultSetExtractor.class), eq(appId)))
                .thenAnswer(invocation -> {
                    ResultSetExtractor<ApplicationMetadata> extractor = invocation.getArgument(1);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.next()).thenReturn(true).thenReturn(false);
                    when(rs.getString("app_id")).thenReturn(parent.getAppId());
                    when(rs.getString("app_name")).thenReturn(parent.getAppName());
                    return extractor.extractData(rs);
                });

        // Mock children fetch (query returns list)
        when(jdbcTemplate.query(eq("app_children.sql content"), any(RowMapper.class), eq(appId)))
                .thenAnswer(invocation -> {
                    RowMapper<ApplicationMetadata> mapper = invocation.getArgument(1);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getString("app_id")).thenReturn(child.getAppId());
                    when(rs.getString("app_name")).thenReturn(child.getAppName());
                    return List.of(mapper.mapRow(rs, 0));
                });

        // Or use anyString() if SqlLoader is not injected
        when(jdbcTemplate.query(contains("SELECT"), any(ResultSetExtractor.class), eq(appId)))
                .thenReturn(parent);

        when(jdbcTemplate.query(contains("application_parent_correlation_id"), any(RowMapper.class), eq(appId)))
                .thenReturn(List.of(child));

        Optional<ApplicationMetadata> result = repo.findByAppId(appId);

        assertTrue(result.isPresent());
        assertEquals(appId, result.get().getAppId());
        assertEquals(1, result.get().getChildren().size());
        assertEquals("Auth Service", result.get().getChildren().get(0).getAppName());
    }


    @Test
    void findByAppId_shouldReturnEmptyWhenNotFound() {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class), any()))
                .thenThrow(new RuntimeException("Not found"));

        Optional<ApplicationMetadata> result = repo.findByAppId("UNKNOWN_APP");

        assertTrue(result.isEmpty());
    }
}
