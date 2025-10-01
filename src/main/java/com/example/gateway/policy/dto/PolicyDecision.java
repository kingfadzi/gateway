// src/main/java/com/example/onboarding/dto/policy/PolicyDecision.java
package com.example.gateway.policy.dto;

import java.util.List;

public record PolicyDecision(
    List<String> arb_domains,
    boolean assessment_mandatory,
    boolean assessment_required,
    boolean attestation_required,
    List<String> fired_rules,
    String policy_version,
    boolean questionnaire_required,
    List<Requirement> requirements,
    String review_mode
) {
    public record Requirement(
        String domain,
        Long due,
        String policy_id,
        ReleaseRef release,
        EvidenceSpec required_evidence_types,
        Scope scope,
        String severity
    ) {
        public record ReleaseRef(String id) {}
        public record Scope(String id, String type) {}
        public record EvidenceSpec(
            java.util.List<EvidenceType> allOf,
            java.util.List<EvidenceType> anyOf,
            java.util.List<EvidenceType> oneOf
        ) {}
        public record EvidenceType(
            String label,
            Integer maxAgeDays,
            String profileField,
            String reviewer,
            java.util.List<String> uiOptions
        ) {}
    }
}
