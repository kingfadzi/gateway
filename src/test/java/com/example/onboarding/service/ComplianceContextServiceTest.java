package com.example.onboarding.service;

import com.example.gateway.profile.dto.ProfileFieldTypeInfo;
import com.example.gateway.registry.dto.ComplianceFramework;
import com.example.gateway.registry.dto.FieldRiskConfig;
import com.example.gateway.registry.dto.RiskCreationRule;
import com.example.gateway.registry.service.ComplianceContextService;
import com.example.gateway.profile.service.ProfileFieldRegistryService;
import com.example.gateway.risk.model.RiskPriority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplianceContextServiceTest {

    @Mock
    private ProfileFieldRegistryService profileFieldRegistryService;

    @InjectMocks
    private ComplianceContextService complianceContextService;

    private ProfileFieldTypeInfo testFieldInfo;
    private FieldRiskConfig testRiskConfig;
    private RiskCreationRule testRule;

    @BeforeEach
    void setUp() {
        // Setup test compliance framework
        ComplianceFramework framework = new ComplianceFramework("Internal", List.of("TBD"));
        
        // Setup test field info
        testFieldInfo = new ProfileFieldTypeInfo(
            "encryption_at_rest",
            "Encryption at Rest", 
            "security",
            "security_rating",
            List.of(framework)
        );
        
        // Setup test rule
        testRule = new RiskCreationRule(
            "required",
            "Required",
            "90d",
            true,
            RiskPriority.HIGH
        );
        
        // Setup test risk config (mock the actual FieldRiskConfig)
        testRiskConfig = new FieldRiskConfig(
            "encryption_at_rest",
            "Encryption at Rest",
            "security_rating", 
            Map.of("A2", testRule)
        );
    }

    @Test
    void getComplianceSnapshot_withAppRating_shouldIncludeActiveRule() {
        // Arrange
        when(profileFieldRegistryService.getFieldTypeInfo("encryption_at_rest"))
            .thenReturn(Optional.of(testFieldInfo));
        when(profileFieldRegistryService.getFieldRiskConfig("encryption_at_rest"))
            .thenReturn(Optional.of(testRiskConfig));

        // Act
        Map<String, Object> snapshot = complianceContextService.getComplianceSnapshot("encryption_at_rest", "A2");

        // Assert
        assertEquals("encryption_at_rest", snapshot.get("fieldKey"));
        assertEquals("Encryption at Rest", snapshot.get("fieldLabel"));
        assertNotNull(snapshot.get("complianceFrameworks"));
        assertNotNull(snapshot.get("snapshotTimestamp"));
        
        // Verify active rule is included
        assertNotNull(snapshot.get("activeRule"));
        @SuppressWarnings("unchecked")
        Map<String, Object> activeRule = (Map<String, Object>) snapshot.get("activeRule");
        assertEquals("A2", activeRule.get("security_rating")); // Uses derived_from field name
        assertEquals("required", activeRule.get("value"));
        assertEquals("Required", activeRule.get("label"));
        assertEquals("90d", activeRule.get("ttl"));
        assertEquals(true, activeRule.get("requiresReview"));
    }

    @Test
    void getComplianceSnapshot_withoutAppRating_shouldNotIncludeActiveRule() {
        // Arrange
        when(profileFieldRegistryService.getFieldTypeInfo("encryption_at_rest"))
            .thenReturn(Optional.of(testFieldInfo));

        // Act
        Map<String, Object> snapshot = complianceContextService.getComplianceSnapshot("encryption_at_rest");

        // Assert
        assertEquals("encryption_at_rest", snapshot.get("fieldKey"));
        assertEquals("Encryption at Rest", snapshot.get("fieldLabel"));
        assertNotNull(snapshot.get("complianceFrameworks"));
        assertNotNull(snapshot.get("snapshotTimestamp"));
        
        // Verify active rule is NOT included
        assertNull(snapshot.get("activeRule"));
    }
}