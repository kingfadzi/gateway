package com.example.gateway.profile.dto;

import java.util.List;

public record DomainPayload(
        String domainKey,
        String title,
        String icon,
        String driverLabel,
        String driverValue,
        List<FieldGraphPayload> fields,
        boolean bulkAttestationEnabled  // Bulk attestation allowed based on CIA+S+R ratings
) {
}