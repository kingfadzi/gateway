package com.example.onboarding.service.application;

import com.example.onboarding.exception.DataIntegrityException;
import java.util.Map;
import java.util.Set;

public final class RatingsNormalizer {
    private RatingsNormalizer(){}

    private static final Set<String> AB_CD = Set.of("A","B","C","D");
    private static final Set<String> A1A2BCD = Set.of("A1","A2","B","C","D");
    private static final Set<String> RES = Set.of("0","1","2","3","4");

    private static String validateStandardRating(String value, String ratingType, String appId) {
        if (value == null || value.trim().isEmpty()) {
            throw new DataIntegrityException("ServiceNow", appId, ratingType.toLowerCase().replace(" ", "_"), 
                    value, ratingType + " rating cannot be null or empty");
        }
        String normalized = value.trim().toUpperCase();
        if (!AB_CD.contains(normalized)) {
            throw new DataIntegrityException("ServiceNow", appId, ratingType.toLowerCase().replace(" ", "_"), 
                    value, ratingType + " rating must be one of " + AB_CD);
        }
        return normalized;
    }

    private static String validateSecurityRating(String value, String appId) {
        if (value == null || value.trim().isEmpty()) {
            throw new DataIntegrityException("ServiceNow", appId, "security_rating", 
                    value, "Security rating cannot be null or empty");
        }
        String normalized = value.trim().toUpperCase();
        if (!A1A2BCD.contains(normalized)) {
            throw new DataIntegrityException("ServiceNow", appId, "security_rating", 
                    value, "Security rating must be one of " + A1A2BCD);
        }
        return normalized;
    }


    private static String validateResilienceRating(String value, String appId) {
        if (value == null || value.trim().isEmpty()) {
            throw new DataIntegrityException("ServiceNow", appId, "resilience_rating", 
                    value, "Resilience rating cannot be null or empty");
        }
        String normalized = value.trim();
        if (!RES.contains(normalized)) {
            throw new DataIntegrityException("ServiceNow", appId, "resilience_rating", 
                    value, "Resilience rating must be one of " + RES);
        }
        return normalized;
    }

    /** Validate and normalize ratings from ServiceNow (source of truth) */
    public static Map<String,Object> normalizeCtx(String appId, String appCriticality, String sec, String conf, String integ, String avail, String resil) {
        return Map.of(
                "app_criticality_assessment",  validateStandardRating(appCriticality, "App criticality", appId),
                "security_rating",  validateSecurityRating(sec, appId),
                "confidentiality_rating", validateStandardRating(conf, "Confidentiality", appId),
                "integrity_rating", validateStandardRating(integ, "Integrity", appId),
                "availability_rating", validateStandardRating(avail, "Availability", appId),
                "resilience_rating", validateResilienceRating(resil, appId)
        );
    }
}
