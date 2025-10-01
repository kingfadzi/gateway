// src/main/java/com/example/onboarding/dto/claims/CreateClaimRequest.java
package com.example.gateway.claims.dto;

public record CreateClaimRequest(
    String releaseId,
    String requirementId,
    String profileFieldExpected,   // dotted or underscore; server normalizes
    String typeExpected,           // e.g., "link" | "file"
    String evidenceId,             // required for "Attach & Submit"
    String method,                 // e.g., "manual" | "system"
    String releaseWindowStartIso,  // optional; if null -> now(UTC)
    Boolean dryRun                 // optional; default false
) {}
