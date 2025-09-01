package com.example.onboarding.service;

import com.example.onboarding.dto.profile.ProfileFieldTypeInfo;
import com.example.onboarding.service.profile.ProfileFieldRegistryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class ProfileFieldDomainTest {

    @Autowired
    private ProfileFieldRegistryService profileFieldRegistryService;

    @Test
    void profileFields_shouldHaveCorrectDomains() {
        // Test security domain
        ProfileFieldTypeInfo encryptionField = profileFieldRegistryService
                .getFieldTypeInfo("encryption_at_rest")
                .orElseThrow();
        assertEquals("security", encryptionField.domain());
        assertEquals("security_rating", encryptionField.derivedFrom());

        // Test availability domain
        ProfileFieldTypeInfo rtoField = profileFieldRegistryService
                .getFieldTypeInfo("rto_hours")
                .orElseThrow();
        assertEquals("availability", rtoField.domain());
        assertEquals("availability_rating", rtoField.derivedFrom());

        // Test integrity domain
        ProfileFieldTypeInfo dataValidationField = profileFieldRegistryService
                .getFieldTypeInfo("data_validation")
                .orElseThrow();
        assertEquals("integrity", dataValidationField.domain());
        assertEquals("integrity_rating", dataValidationField.derivedFrom());

        // Test confidentiality domain
        ProfileFieldTypeInfo confidentialityField = profileFieldRegistryService
                .getFieldTypeInfo("confidentiality_level")
                .orElseThrow();
        assertEquals("confidentiality", confidentialityField.domain());
        assertEquals("confidentiality_rating", confidentialityField.derivedFrom());

        // Test resilience domain
        ProfileFieldTypeInfo drField = profileFieldRegistryService
                .getFieldTypeInfo("dr_test_frequency")
                .orElseThrow();
        assertEquals("resilience", drField.domain());
        assertEquals("resilience_rating", drField.derivedFrom());

        // Test artifact domain (special case without _rating suffix)
        ProfileFieldTypeInfo artifactField = profileFieldRegistryService
                .getFieldTypeInfo("product_vision")
                .orElseThrow();
        assertEquals("artifact", artifactField.domain());
        assertEquals("artifact", artifactField.derivedFrom());
    }
}