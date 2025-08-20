// src/main/java/com/example/onboarding/requirements/view/RequirementsView.java
package com.example.onboarding.view;

import java.util.List;

public record RequirementsView(
        String reviewMode,
        Assessment assessment,
        List<String> domains,
        List<RequirementView> requirements,
        List<String> firedRules,
        String policyVersion
) {
    public record Assessment(boolean required, boolean mandatory, boolean questionnaireRequired) {}

    public record RequirementView(
            String id,             // policy_id
            String domain,
            String scope,          // "profile" | "release"
            String severity,
            String due,            // e.g., "OPA:1756253302870855808"
            String dueDateDisplay, // ISO if provided (release window)
            Parts parts
    ) {}

    public record Parts(
            List<Part> allOf,
            List<Part> anyOf,
            List<Part> oneOf
    ) {}

    public record Part(
            String label,
            String profileField,
            Integer maxAgeDays,
            String reviewer,
            List<String> uiOptions
    ) {}
}
