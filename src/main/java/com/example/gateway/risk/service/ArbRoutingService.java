package com.example.gateway.risk.service;

import com.example.gateway.profile.service.ProfileFieldRegistryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service for routing risk items to appropriate Architecture Review Boards (ARBs).
 *
 * Uses the field registry to determine which ARB should handle risks for a given field.
 * ARB routing is based on the field's derived_from value (e.g., security_rating -> security_arb).
 */
@Service
public class ArbRoutingService {

    private static final Logger log = LoggerFactory.getLogger(ArbRoutingService.class);

    private final ProfileFieldRegistryService registryService;

    public ArbRoutingService(ProfileFieldRegistryService registryService) {
        this.registryService = registryService;
    }

    /**
     * Determine which ARB should handle a risk for the given field.
     *
     * @param fieldKey The profile field key (e.g., "confidentiality_level")
     * @return ARB identifier from registry (e.g., "security"), or "default" if routing not found
     */
    public String getArbForField(String fieldKey) {
        if (fieldKey == null || fieldKey.isEmpty()) {
            log.warn("Cannot determine ARB for null/empty field key");
            return "default";
        }

        Optional<String> arb = registryService.getArbForField(fieldKey);

        if (arb.isEmpty()) {
            log.warn("No ARB routing found for field: {}, using default", fieldKey);
            return "default";
        }

        return arb.get();
    }

    /**
     * Get ARB directly from derived_from field.
     *
     * @param derivedFrom The derived_from value (e.g., "security_rating")
     * @return ARB identifier from registry (e.g., "security"), or "default" if not found
     */
    public String getArbForDerivedFrom(String derivedFrom) {
        if (derivedFrom == null || derivedFrom.isEmpty()) {
            log.warn("Cannot determine ARB for null/empty derived_from");
            return "default";
        }

        Optional<String> arb = registryService.getArbForDerivedFrom(derivedFrom);

        if (arb.isEmpty()) {
            log.warn("No ARB routing found for derived_from: {}, using default", derivedFrom);
            return "default";
        }

        return arb.get();
    }

    /**
     * Calculate domain name from derived_from field.
     * Domain is typically the derived_from value with "_rating" suffix removed.
     *
     * Examples:
     * - "security_rating" -> "security"
     * - "integrity_rating" -> "integrity"
     * - "availability_rating" -> "availability"
     *
     * @param derivedFrom The derived_from value
     * @return Domain name (e.g., "security")
     */
    public String calculateDomain(String derivedFrom) {
        if (derivedFrom == null || derivedFrom.isEmpty()) {
            return "unknown";
        }

        // Remove _rating suffix to get domain name
        if (derivedFrom.endsWith("_rating")) {
            return derivedFrom.substring(0, derivedFrom.length() - 7);
        }

        // For special cases like "artifact", return as-is
        return derivedFrom;
    }

    /**
     * Get human-readable ARB name from ARB identifier.
     * Can be used for display purposes.
     *
     * @param arbId ARB identifier from registry (e.g., "security")
     * @return Human-readable name (e.g., "Security ARB")
     */
    public String getArbDisplayName(String arbId) {
        if (arbId == null || arbId.isEmpty()) {
            return "Unknown ARB";
        }

        return switch (arbId.toLowerCase()) {
            case "security" -> "Security ARB";
            case "data" -> "Data ARB";
            case "operations" -> "Operations ARB";
            case "enterprise_architecture" -> "Enterprise Architecture ARB";
            case "default" -> "Default ARB";
            default -> capitalizeWords(arbId.replace("_", " ")) + " ARB";
        };
    }

    /**
     * Helper method to capitalize words in a string.
     */
    private String capitalizeWords(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }

        String[] words = str.split("\\s+");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (!word.isEmpty()) {
                if (!result.isEmpty()) {
                    result.append(" ");
                }
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1).toLowerCase());
                }
            }
        }

        return result.toString();
    }
}
