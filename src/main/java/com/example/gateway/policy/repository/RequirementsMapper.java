// src/main/java/com/example/onboarding/requirements/RequirementsMapper.java
package com.example.gateway.policy.repository;

import com.example.gateway.policy.dto.PolicyDecision;
import com.example.gateway.requirements.dto.RequirementsView;

import java.time.OffsetDateTime;
import java.util.List;

public final class RequirementsMapper {

    private RequirementsMapper() {}

    public static RequirementsView toView(PolicyDecision decision, String releaseWindowStartIso) {
        var assessment = new RequirementsView.Assessment(
                decision.assessment_required(),
                decision.assessment_mandatory(),
                decision.questionnaire_required()
        );

        var reqViews = decision.requirements().stream().map(r -> {
            String scope = (r.release() != null && r.release().id() != null) ? "release" : "profile";

            var allOf = (r.required_evidence_types() != null && r.required_evidence_types().allOf() != null)
                    ? r.required_evidence_types().allOf().stream().map(et ->
                    new RequirementsView.Part(
                            et.label(), et.profileField(), et.maxAgeDays(), et.reviewer(), et.uiOptions(),
                            null // reuseCandidate (filled later)
                    )
            ).toList()
                    : List.<RequirementsView.Part>of();

            var anyOf = (r.required_evidence_types() != null && r.required_evidence_types().anyOf() != null)
                    ? r.required_evidence_types().anyOf().stream().map(et ->
                    new RequirementsView.Part(
                            et.label(), et.profileField(), et.maxAgeDays(), et.reviewer(), et.uiOptions(),
                            null
                    )
            ).toList()
                    : List.<RequirementsView.Part>of();

            var oneOf = (r.required_evidence_types() != null && r.required_evidence_types().oneOf() != null)
                    ? r.required_evidence_types().oneOf().stream().map(et ->
                    new RequirementsView.Part(
                            et.label(), et.profileField(), et.maxAgeDays(), et.reviewer(), et.uiOptions(),
                            null
                    )
            ).toList()
                    : List.<RequirementsView.Part>of();

            String dueDateDisplay = null;
            if (releaseWindowStartIso != null && !releaseWindowStartIso.isBlank()) {
                try { OffsetDateTime.parse(releaseWindowStartIso); dueDateDisplay = releaseWindowStartIso; }
                catch (Exception ignored) {}
            }

            return new RequirementsView.RequirementView(
                    r.policy_id(),
                    r.domain(),
                    scope,
                    r.severity(),
                    r.due() == null ? null : "OPA:" + r.due(),
                    dueDateDisplay,
                    new RequirementsView.Parts(allOf, anyOf, oneOf)
            );
        }).toList();

        return new RequirementsView(
                decision.review_mode(),
                assessment,
                decision.arb_domains(),
                reqViews,
                decision.fired_rules(),
                decision.policy_version()
        );
    }
}
