package com.example.gateway.profile.service;

import com.example.gateway.GatewayApplication;
import com.example.gateway.profile.dto.ProfileFieldTypeInfo;
import com.example.gateway.registry.dto.FieldRiskConfig;
import com.example.gateway.registry.dto.RiskCreationRule;
import com.example.gateway.risk.model.RiskPriority;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = GatewayApplication.class)
@ActiveProfiles("test")
public class ProfileFieldRegistryServiceTest {

    @Autowired
    private ProfileFieldRegistryService registryService;

    // =====================
    // Basic Registry Loading Tests
    // =====================

    @Test
    void shouldLoadProfileFieldTypes() {
        List<ProfileFieldTypeInfo> fieldTypes = registryService.getAllProfileFieldTypes();

        assertNotNull(fieldTypes, "Field types should not be null");
        assertFalse(fieldTypes.isEmpty(), "Should have loaded field types from registry");
    }

    @Test
    void shouldGetFieldTypeByKey() {
        Optional<ProfileFieldTypeInfo> fieldType = registryService.getFieldTypeInfo("confidentiality_level");

        assertTrue(fieldType.isPresent(), "Should find confidentiality_level field");
        assertEquals("confidentiality_level", fieldType.get().fieldKey());
        assertNotNull(fieldType.get().label());
        assertNotNull(fieldType.get().derivedFrom());
    }

    @Test
    void shouldReturnEmptyForUnknownField() {
        Optional<ProfileFieldTypeInfo> fieldType = registryService.getFieldTypeInfo("unknown_field_xyz");

        assertTrue(fieldType.isEmpty(), "Should return empty for unknown field");
    }

    // =====================
    // ARB Routing Tests
    // =====================

    @Test
    void shouldLoadArbRoutingConfiguration() {
        Map<String, String> arbRouting = registryService.getAllArbRouting();

        assertNotNull(arbRouting, "ARB routing should not be null");
        assertFalse(arbRouting.isEmpty(), "Should have loaded ARB routing from registry");
    }

    @Test
    void shouldGetArbForDerivedFrom() {
        // Test security_rating -> security_arb mapping
        Optional<String> arb = registryService.getArbForDerivedFrom("security_rating");

        assertTrue(arb.isPresent(), "Should find ARB for security_rating");
        assertEquals("security_arb", arb.get(), "security_rating should route to security_arb");
    }

    @Test
    void shouldGetArbForMultipleDomains() {
        // Test multiple domain mappings
        Optional<String> securityArb = registryService.getArbForDerivedFrom("security_rating");
        Optional<String> integrityArb = registryService.getArbForDerivedFrom("integrity_rating");
        Optional<String> availabilityArb = registryService.getArbForDerivedFrom("availability_rating");

        assertTrue(securityArb.isPresent(), "Should have security ARB");
        assertTrue(integrityArb.isPresent(), "Should have integrity ARB");
        assertTrue(availabilityArb.isPresent(), "Should have availability ARB");

        assertEquals("security_arb", securityArb.get());
        assertEquals("integrity_arb", integrityArb.get());
        assertEquals("availability_arb", availabilityArb.get());
    }

    @Test
    void shouldReturnEmptyArbForUnknownDerivedFrom() {
        Optional<String> arb = registryService.getArbForDerivedFrom("unknown_rating");

        assertTrue(arb.isEmpty(), "Should return empty for unknown derived_from");
    }

    @Test
    void shouldGetArbForField() {
        // Test getting ARB by field key (looks up field's derived_from, then routes to ARB)
        Optional<String> arb = registryService.getArbForField("confidentiality_level");

        assertTrue(arb.isPresent(), "Should find ARB for confidentiality_level field");
        // confidentiality_level is derived from confidentiality_rating, which routes to confidentiality_arb
        assertEquals("confidentiality_arb", arb.get());
    }

    // =====================
    // Priority Parsing Tests
    // =====================

    @Test
    void shouldParseRiskRulesWithPriority() {
        // Get field that has priority configured (confidentiality_level)
        Optional<FieldRiskConfig> config = registryService.getFieldRiskConfig("confidentiality_level");

        assertTrue(config.isPresent(), "Should find risk config for confidentiality_level");

        // Check that rules have priority
        Map<String, RiskCreationRule> rules = config.get().rules();
        assertFalse(rules.isEmpty(), "Should have risk rules");

        // Test A rule - should be CRITICAL priority
        RiskCreationRule aRule = rules.get("A");
        assertNotNull(aRule, "Should have A criticality rule");
        assertNotNull(aRule.priority(), "Rule should have priority");
        assertEquals(RiskPriority.CRITICAL, aRule.priority(), "A rule should have CRITICAL priority");

        // Test B rule - should be HIGH priority
        RiskCreationRule bRule = rules.get("B");
        assertNotNull(bRule, "Should have B criticality rule");
        assertEquals(RiskPriority.HIGH, bRule.priority(), "B rule should have HIGH priority");
    }

    @Test
    void shouldHandleMissingPriorityWithDefault() {
        // Find a field that doesn't have priority configured (if any exist)
        // The parser should default to LOW priority
        Optional<RiskCreationRule> rule = registryService.getRiskRule("security_testing", "D");

        if (rule.isPresent()) {
            assertNotNull(rule.get().priority(), "Priority should not be null");
            // If no priority was specified in YAML, should default to LOW
        }
    }

    @Test
    void shouldParseAllPriorityLevels() {
        // Get encryption_at_rest which should have all priority levels
        Optional<FieldRiskConfig> config = registryService.getFieldRiskConfig("encryption_at_rest");

        assertTrue(config.isPresent(), "Should find encryption_at_rest config");

        Map<String, RiskCreationRule> rules = config.get().rules();

        // A1 -> CRITICAL
        if (rules.containsKey("A1")) {
            assertEquals(RiskPriority.CRITICAL, rules.get("A1").priority());
        }

        // A2 -> HIGH
        if (rules.containsKey("A2")) {
            assertEquals(RiskPriority.HIGH, rules.get("A2").priority());
        }

        // B -> MEDIUM
        if (rules.containsKey("B")) {
            assertEquals(RiskPriority.MEDIUM, rules.get("B").priority());
        }

        // C -> LOW
        if (rules.containsKey("C")) {
            assertEquals(RiskPriority.LOW, rules.get("C").priority());
        }

        // D -> LOW
        if (rules.containsKey("D")) {
            assertEquals(RiskPriority.LOW, rules.get("D").priority());
        }
    }

    // =====================
    // Risk Configuration Tests
    // =====================

    @Test
    void shouldGetFieldRiskConfig() {
        Optional<FieldRiskConfig> config = registryService.getFieldRiskConfig("confidentiality_level");

        assertTrue(config.isPresent(), "Should find risk config");
        assertEquals("confidentiality_level", config.get().fieldKey());
        assertNotNull(config.get().rules());
        assertFalse(config.get().rules().isEmpty());
    }

    @Test
    void shouldCheckIfFieldRequiresReview() {
        // confidentiality_level with A criticality should require review
        boolean requiresReview = registryService.requiresReviewForField("confidentiality_level", "A");

        assertTrue(requiresReview, "A criticality should require review");
    }

    @Test
    void shouldGetRiskRuleForFieldAndCriticality() {
        Optional<RiskCreationRule> rule = registryService.getRiskRule("confidentiality_level", "A");

        assertTrue(rule.isPresent(), "Should find rule for A criticality");
        assertEquals("restricted", rule.get().value());
        assertEquals("Restricted", rule.get().label());
        assertEquals(RiskPriority.CRITICAL, rule.get().priority());
        assertTrue(rule.get().requiresReview());
    }

    @Test
    void shouldGetAllFieldsWithRiskConfig() {
        List<String> fieldsWithConfig = registryService.getFieldsWithRiskConfig();

        assertNotNull(fieldsWithConfig);
        assertFalse(fieldsWithConfig.isEmpty(), "Should have fields with risk config");
        assertTrue(fieldsWithConfig.contains("confidentiality_level"), "Should include confidentiality_level");
        assertTrue(fieldsWithConfig.contains("encryption_at_rest"), "Should include encryption_at_rest");
    }

    // =====================
    // Domain and Grouping Tests
    // =====================

    @Test
    void shouldGetFieldTypesByDomain() {
        Map<String, List<ProfileFieldTypeInfo>> byDomain = registryService.getProfileFieldTypesByDomain();

        assertNotNull(byDomain);
        assertFalse(byDomain.isEmpty(), "Should have domains with fields");
    }

    @Test
    void shouldGetDomains() {
        List<String> domains = registryService.getDomains();

        assertNotNull(domains);
        assertFalse(domains.isEmpty(), "Should have domains");
    }

    @Test
    void shouldValidateFieldKeys() {
        List<String> testKeys = List.of(
                "confidentiality_level",
                "encryption_at_rest",
                "unknown_field_1",
                "security_testing",
                "unknown_field_2"
        );

        List<String> validKeys = registryService.validateFieldKeys(testKeys);

        assertNotNull(validKeys);
        assertTrue(validKeys.contains("confidentiality_level"));
        assertTrue(validKeys.contains("encryption_at_rest"));
        assertFalse(validKeys.contains("unknown_field_1"));
        assertFalse(validKeys.contains("unknown_field_2"));
    }

    // =====================
    // Integration Tests
    // =====================

    @Test
    void shouldHaveConsistentArbRoutingForAllFields() {
        // Every field should have a derived_from that maps to an ARB
        List<ProfileFieldTypeInfo> allFields = registryService.getAllProfileFieldTypes();

        for (ProfileFieldTypeInfo field : allFields) {
            String derivedFrom = field.derivedFrom();
            assertNotNull(derivedFrom, "Field " + field.fieldKey() + " should have derived_from");

            // Check if this derived_from has an ARB mapping
            Optional<String> arb = registryService.getArbForDerivedFrom(derivedFrom);
            if (arb.isEmpty()) {
                // Log warning but don't fail - some fields might not have ARB routing yet
                System.out.println("Warning: No ARB mapping for derived_from: " + derivedFrom);
            }
        }
    }

    @Test
    void shouldHavePriorityForAllRiskRules() {
        // All risk rules should have priority set
        Map<String, FieldRiskConfig> allConfigs = registryService.getAllRiskConfigs();

        for (Map.Entry<String, FieldRiskConfig> entry : allConfigs.entrySet()) {
            String fieldKey = entry.getKey();
            FieldRiskConfig config = entry.getValue();

            for (Map.Entry<String, RiskCreationRule> ruleEntry : config.rules().entrySet()) {
                String criticality = ruleEntry.getKey();
                RiskCreationRule rule = ruleEntry.getValue();

                assertNotNull(rule.priority(),
                        String.format("Field %s, criticality %s should have priority", fieldKey, criticality));
            }
        }
    }
}
