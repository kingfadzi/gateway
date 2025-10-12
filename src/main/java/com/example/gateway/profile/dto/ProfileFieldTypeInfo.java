package com.example.gateway.profile.dto;

import com.example.gateway.registry.dto.ComplianceFramework;
import java.util.List;

/**
 * Profile field type information for frontend dropdowns
 */
public record ProfileFieldTypeInfo(
        String fieldKey,
        String label,
        String domain,       // security, confidentiality, integrity, availability, resilience, app_criticality
        String derivedFrom,   // underlying rating that drives this field
        String arb,          // ARB responsible for this field: security, data, operations, enterprise_architecture
        List<ComplianceFramework> complianceFrameworks  // compliance frameworks this field maps to
) {}