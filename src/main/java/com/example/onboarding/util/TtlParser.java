package com.example.onboarding.util;

import java.time.Duration;
import java.util.Map;

/**
 * Utility for parsing TTL values from profile field policy requirements
 */
public final class TtlParser {
    private TtlParser() {}
    
    /**
     * Extract TTL Duration from profile field policy value
     * @param policyValue The policy JSON object from profile field
     * @return Duration representing the TTL, defaults to 365 days if not found
     */
    public static Duration extractTtl(Object policyValue) {
        if (policyValue == null) {
            return Duration.ofDays(365); // Default 1 year
        }
        
        if (policyValue instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> policyMap = (Map<String, Object>) policyValue;
            Object ttlObj = policyMap.get("ttl");
            
            if (ttlObj instanceof String) {
                return parseTtlString((String) ttlObj);
            }
        }
        
        return Duration.ofDays(365); // Default fallback
    }
    
    /**
     * Parse TTL string format (e.g., "90d", "30m", "24h", "1y")
     * @param ttlString The TTL string from policy configuration
     * @return Duration representing the TTL
     */
    public static Duration parseTtlString(String ttlString) {
        if (ttlString == null || ttlString.trim().isEmpty()) {
            return Duration.ofDays(365); // Default 1 year
        }
        
        String ttl = ttlString.trim().toLowerCase();
        
        try {
            if (ttl.endsWith("d")) {
                int days = Integer.parseInt(ttl.substring(0, ttl.length() - 1));
                return Duration.ofDays(days);
            } else if (ttl.endsWith("h")) {
                int hours = Integer.parseInt(ttl.substring(0, ttl.length() - 1));
                return Duration.ofHours(hours);
            } else if (ttl.endsWith("m")) {
                int minutes = Integer.parseInt(ttl.substring(0, ttl.length() - 1));
                return Duration.ofMinutes(minutes);
            } else if (ttl.endsWith("y")) {
                int years = Integer.parseInt(ttl.substring(0, ttl.length() - 1));
                return Duration.ofDays(years * 365);
            } else {
                // Try parsing as number of days
                int days = Integer.parseInt(ttl);
                return Duration.ofDays(days);
            }
        } catch (NumberFormatException e) {
            // Invalid format, use default
            return Duration.ofDays(365);
        }
    }
}