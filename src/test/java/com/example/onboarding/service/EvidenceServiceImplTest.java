package com.example.onboarding.service;

import com.example.gateway.config.FieldRegistryConfig;
import com.example.gateway.evidence.dto.EvidenceSearchRequest;
import com.example.gateway.evidence.dto.WorkbenchEvidenceItem;
import com.example.gateway.evidence.repository.EvidenceRepository;
import com.example.gateway.evidence.repository.EvidenceKpiRepository;
import com.example.gateway.evidence.repository.EvidenceSearchRepository;
import com.example.gateway.evidence.repository.EvidenceDocumentRepository;
import com.example.gateway.profile.respository.ProfileRepository;
import com.example.gateway.document.service.DocumentService;
import com.example.gateway.evidence.service.EvidenceFieldLinkService;
import com.example.gateway.evidence.service.EvidenceServiceImpl;
import com.example.gateway.evidence.service.UnifiedFreshnessCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class EvidenceServiceImplTest {

    @Mock
    private EvidenceRepository evidenceRepository;

    @Mock
    private EvidenceKpiRepository evidenceKpiRepository;

    @Mock
    private EvidenceSearchRepository evidenceSearchRepository;

    @Mock
    private EvidenceDocumentRepository evidenceDocumentRepository;

    @Mock
    private DocumentService documentService;

    @Mock
    private EvidenceFieldLinkService evidenceFieldLinkService;

    @Mock
    private ProfileRepository profileRepository;

    @Mock
    private UnifiedFreshnessCalculator unifiedFreshnessCalculator;

    @Mock
    private FieldRegistryConfig fieldRegistryConfig;

    @InjectMocks
    private EvidenceServiceImpl evidenceService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testSearchWorkbenchEvidence() {
        // Given
        EvidenceSearchRequest request = new EvidenceSearchRequest();
        request.setLimit(10);
        request.setOffset(0);

        Map<String, Object> rawRow = Map.of(
                "evidence_id", "ev-123",
                "app_id", "app-456",
                "app_name", "Test App",
                "field_key", "test-field",
                "profile_field_id", "pf-789",
                "risk_count", 1
        );

        when(evidenceSearchRepository.searchWorkbenchEvidence(request)).thenReturn(List.of(rawRow));
        when(fieldRegistryConfig.getDerivedFromByFieldKey("test-field")).thenReturn("security_rating");
        when(unifiedFreshnessCalculator.calculateFreshness(any(), any())).thenReturn("current");

        // When
        List<WorkbenchEvidenceItem> result = evidenceService.searchWorkbenchEvidence(request);

        // Then
        assertEquals(1, result.size());
        WorkbenchEvidenceItem item = result.get(0);
        assertEquals("ev-123", item.getEvidenceId());
        assertEquals("app-456", item.getAppId());
        assertEquals("Test App", item.getAppName());
        assertEquals("test-field", item.getFieldKey());
        assertEquals("Security", item.getDomainTitle());
        assertEquals("current", item.getFreshnessStatus());
    }
}
