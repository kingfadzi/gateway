package com.example.gateway.claims.dto;

/**
 * Request DTO for ensuring a claim exists for a given profile field and track
 */
public record EnsureClaimRequest(
        String fieldKey,         // profile field key (e.g., "encryption_at_rest")
        String method           // claim method: "manual", "auto", "imported" (defaults to "manual")
) {}