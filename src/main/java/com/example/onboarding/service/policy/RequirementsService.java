// src/main/java/com/example/onboarding/requirements/RequirementsService.java
package com.example.onboarding.service.policy;

import com.example.onboarding.dto.policy.OpaRequest;
import com.example.onboarding.dto.policy.PolicyDecision;
import com.example.onboarding.dto.policy.PolicyInput;
import com.example.onboarding.repository.policy.RequirementsMapper;
import com.example.onboarding.dto.policy.RequirementsView;
import com.example.onboarding.dto.policy.ReuseCandidate;
import com.example.onboarding.integrations.OpaClient;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Service
public class RequirementsService {

    private final OpaClient opaClient;
    private final EvidenceReuseService reuseService;

    public RequirementsService(OpaClient opaClient, EvidenceReuseService reuseService) {
        this.opaClient = opaClient;
        this.reuseService = reuseService;
    }

    public RequirementsView getRequirements(
            String appId,
            String releaseId,
            String releaseWindowStartIso,
            String criticality,
            String security,
            String integrity,
            String availability,
            String resilience,
            boolean hasDependencies
    ) {
        // 1) Call OPA
        var input = new PolicyInput(
                new PolicyInput.App(appId),
                criticality, security, integrity, availability, resilience,
                hasDependencies,
                (releaseId == null ? null : new PolicyInput.Release(releaseId, releaseWindowStartIso))
        );
        PolicyDecision decision = opaClient.evaluate(new OpaRequest(input));

        // 2) Map to FE shape
        RequirementsView base = RequirementsMapper.toView(decision, releaseWindowStartIso);

        // 3) Decorate with reuse candidates
        OffsetDateTime parsed = parseOdt(releaseWindowStartIso);
        final OffsetDateTime asOf = (parsed != null) ? parsed : OffsetDateTime.now();

        List<RequirementsView.RequirementView> decoratedReqs = new ArrayList<>();
        for (var req : base.requirements()) {
            var newAllOf = req.parts().allOf() == null ? List.<RequirementsView.Part>of() :
                    req.parts().allOf().stream().map(p -> pWithCandidate(appId, p, asOf)).toList();

            var newAnyOf = req.parts().anyOf() == null ? List.<RequirementsView.Part>of() :
                    req.parts().anyOf().stream().map(p -> pWithCandidate(appId, p, asOf)).toList();

            var newOneOf = req.parts().oneOf() == null ? List.<RequirementsView.Part>of() :
                    req.parts().oneOf().stream().map(p -> pWithCandidate(appId, p, asOf)).toList();

            var newParts = new RequirementsView.Parts(newAllOf, newAnyOf, newOneOf);
            decoratedReqs.add(new RequirementsView.RequirementView(
                    req.id(), req.domain(), req.scope(), req.severity(), req.due(), req.dueDateDisplay(), newParts
            ));
        }

        return new RequirementsView(
                base.reviewMode(),
                base.assessment(),
                base.domains(),
                decoratedReqs,
                base.firedRules(),
                base.policyVersion()
        );
    }


    private RequirementsView.Part pWithCandidate(String appId, RequirementsView.Part p, OffsetDateTime asOf) {
        ReuseCandidate cand = reuseService
                .findBestReusable(appId, p.profileField(), p.maxAgeDays(), asOf)
                .orElse(null);
        return new RequirementsView.Part(
                p.label(), p.profileField(), p.maxAgeDays(), p.reviewer(), p.uiOptions(), cand
        );
    }

    private static OffsetDateTime parseOdt(String iso) {
        if (iso == null || iso.isBlank()) return null;
        try { return OffsetDateTime.parse(iso); } catch (DateTimeParseException e) { return null; }
    }
}
