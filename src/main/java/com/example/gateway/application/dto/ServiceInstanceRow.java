package com.example.gateway.application.dto;

/** One non-Dev service instance for an application (sourced from SNOW views). */
public record ServiceInstanceRow(
        String itServiceInstanceSysid,   // PK in target table
        String environment,
        String itBusinessServiceSysid,
        String businessApplicationSysid,
        String serviceOfferingJoin,
        String serviceInstance,          // display/name
        String installType,
        String serviceClassification
) {}
