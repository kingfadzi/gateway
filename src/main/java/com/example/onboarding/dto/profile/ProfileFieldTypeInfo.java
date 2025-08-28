package com.example.onboarding.dto.profile;

/**
 * Profile field type information for frontend dropdowns
 */
public record ProfileFieldTypeInfo(
        String fieldKey,
        String label,
        String domain,       // security, confidentiality, integrity, availability, resilience, app_criticality
        String derivedFrom   // underlying rating that drives this field
) {}