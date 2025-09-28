package com.example.onboarding.service;

import com.example.onboarding.config.FieldRegistryConfig;
import com.example.onboarding.dto.evidence.EvidenceSearchRequest;
import com.example.onboarding.dto.evidence.WorkbenchEvidenceItem;
import com.example.onboarding.dto.profile.ProfileField;
import com.example.onboarding.repository.evidence.EvidenceRepository;
import com.example.onboarding.repository.profile.ProfileRepository;
import com.example.onboarding.service.document.DocumentService;
import com.example.onboarding.service.evidence.EvidenceFieldLinkService;
import com.example.onboarding.service.evidence.EvidenceServiceImpl;
import com.example.onboarding.service.evidence.UnifiedFreshnessCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class EvidenceServiceImplTest {

    @Mock
    private EvidenceRepository evidenceRepository;

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

        when(evidenceRepository.searchWorkbenchEvidence(request)).thenReturn(List.of(rawRow));
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
