package com.example.onboarding.service;

import com.example.onboarding.registry.dto.RegistryRuleEvaluation;
import com.example.onboarding.registry.service.RegistryRiskConfigService;
import com.example.onboarding.profile.service.ProfileFieldRegistryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class RegistryRiskConfigServiceTest {

    @Autowired
    private RegistryRiskConfigService registryRiskConfigService;
    
    @Autowired 
    private ProfileFieldRegistryService profileFieldRegistryService;

    @Test
    void shouldCreateRiskForHighCriticalitySecurityTesting() {
        // Test that A1 criticality security_testing requires review
        RegistryRuleEvaluation evaluation = registryRiskConfigService.evaluateRiskCreation(
                "security_testing", "test-app", "A1"
        );
        
        assertTrue(evaluation.shouldCreateRisk(), "A1 security_testing should require review");
        assertEquals("security_testing", evaluation.fieldKey());
        assertEquals("A1", evaluation.appCriticality());
        assertNotNull(evaluation.matchedRule());
        assertTrue(evaluation.matchedRule().requiresReview());
    }
    
    @Test 
    void shouldNotCreateRiskForLowCriticalitySecurityTesting() {
        // Test that C criticality security_testing does not require review
        RegistryRuleEvaluation evaluation = registryRiskConfigService.evaluateRiskCreation(
                "security_testing", "test-app", "C"
        );
        
        assertFalse(evaluation.shouldCreateRisk(), "C criticality security_testing should not require review");
        assertEquals("security_testing", evaluation.fieldKey());
        assertEquals("C", evaluation.appCriticality());
        assertNotNull(evaluation.matchedRule());
        assertFalse(evaluation.matchedRule().requiresReview());
    }
    
    @Test
    void shouldCreateRiskForHighCriticalityEncryption() {
        // Test that A1 criticality encryption_at_rest requires review
        RegistryRuleEvaluation evaluation = registryRiskConfigService.evaluateRiskCreation(
                "encryption_at_rest", "test-app", "A1"
        );
        
        assertTrue(evaluation.shouldCreateRisk(), "A1 encryption_at_rest should require review");
    }
    
    @Test
    void shouldReturnNoRuleForUnknownField() {
        // Test unknown field returns no rule found
        RegistryRuleEvaluation evaluation = registryRiskConfigService.evaluateRiskCreation(
                "unknown_field", "test-app", "A1"
        );
        
        assertFalse(evaluation.shouldCreateRisk());
        assertNull(evaluation.matchedRule());
        assertTrue(evaluation.evaluationReason().contains("No registry rule found"));
    }
    
    @Test
    void shouldLoadRiskConfigurations() {
        // Test that risk configurations are loaded
        var fieldsWithConfig = registryRiskConfigService.getFieldsRequiringReview();
        
        assertFalse(fieldsWithConfig.isEmpty(), "Should have fields requiring review");
        assertTrue(fieldsWithConfig.contains("security_testing"), "Should include security_testing");
        assertTrue(fieldsWithConfig.contains("encryption_at_rest"), "Should include encryption_at_rest");
        assertTrue(fieldsWithConfig.contains("secrets_management"), "Should include secrets_management");
    }
    
    @Test
    void shouldCheckCriticalitySpecificFields() {
        // Test getting fields that require review for A1 criticality
        var a1Fields = registryRiskConfigService.getFieldsRequiringReviewForCriticality("A1");
        var dFields = registryRiskConfigService.getFieldsRequiringReviewForCriticality("D");
        
        assertTrue(a1Fields.contains("security_testing"), "A1 should include security_testing");
        assertFalse(dFields.contains("security_testing"), "D should not include security_testing");
    }
    
    @Test
    void shouldProvideSummary() {
        // Test configuration summary
        String summary = registryRiskConfigService.getRiskConfigurationSummary();
        
        assertNotNull(summary);
        assertTrue(summary.contains("Risk Configuration:"));
        assertTrue(summary.contains("total fields"));
        assertTrue(summary.contains("require review"));
    }
}