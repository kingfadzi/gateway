// src/main/java/com/example/onboarding/service/policy/RequirementsService.java
package com.example.onboarding.service.requirements;

import com.example.onboarding.dto.policy.OpaRequest;
import com.example.onboarding.dto.policy.PolicyDecision;
import com.example.onboarding.dto.policy.PolicyInput;
import com.example.onboarding.dto.requirements.RequirementsView;
import com.example.onboarding.dto.requirements.RequirementsView.RequirementView;
import com.example.onboarding.dto.requirements.RequirementsView.Parts;
import com.example.onboarding.dto.requirements.RequirementsView.Part;
import com.example.onboarding.dto.evidence.ReuseCandidate;                  // <-- use dto.evidence
import com.example.onboarding.integrations.OpaClient;
import com.example.onboarding.repository.evidence.EvidenceReuseRepository;   // profile context reader
import com.example.onboarding.repository.policy.RequirementsMapper;         // OPA -> FE mapper
import com.example.onboarding.service.evidence.EvidenceReuseService;        // <-- correct service package
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class RequirementsService {

    private static final Logger log = LoggerFactory.getLogger(RequirementsService.class);

    private final OpaClient opaClient;
    private final EvidenceReuseRepository profileReader;
    private final EvidenceReuseService reuseService;

    public RequirementsService(OpaClient opaClient,
                               EvidenceReuseRepository profileReader,
                               EvidenceReuseService reuseService) {
        this.opaClient = opaClient;
        this.profileReader = profileReader;
        this.reuseService = reuseService;
    }

    /** C2 + C4: read profile → OPA → map → decorate parts with reuseCandidate (records are immutable). */
    public RequirementsView getRequirements(String appId, String releaseId, String releaseWindowStartIso) {
        // 1) Read normalized policy context
        Map<String, Object> ctx = profileReader.readPolicyContext(appId);

        String criticality  = stringOrDefault(ctx.get("app_criticality"), "C");
        String security     = stringOrDefault(ctx.get("security_rating"), "A2");
        String integrity    = stringOrDefault(ctx.get("integrity_rating"), "C");
        String availability = stringOrDefault(ctx.get("availability_rating"), "C");
        String resilience   = stringOrDefault(ctx.get("resilience_rating"), "2");
        boolean hasDeps     = booleanOrDefault(ctx.get("has_dependencies"), true);

        // 2) Build OPA input
        var input = new PolicyInput(
                new PolicyInput.App(appId),
                criticality, security, integrity, availability, resilience,
                hasDeps,
                new PolicyInput.Release(releaseId, releaseWindowStartIso)
        );

        // 3) Policy decision
        PolicyDecision decision = opaClient.evaluate(new OpaRequest(input));

        // 4) Map to FE view (immutable record)
        RequirementsView base = RequirementsMapper.toView(decision, releaseWindowStartIso);

        // 5) Decorate (OffsetDateTime)
        final OffsetDateTime asOf = parseAsOfOffset(releaseWindowStartIso);

        List<RequirementView> decoratedReqs = base.requirements().stream()
            .map(req -> {
                Parts p = req.parts();
                List<Part> allOf = decorateList(p.allOf(), appId, asOf);
                List<Part> anyOf = decorateList(p.anyOf(), appId, asOf);
                List<Part> oneOf = decorateList(p.oneOf(), appId, asOf);
                return new RequirementView(
                        req.id(), req.domain(), req.scope(),
                        req.severity(), req.due(), req.dueDateDisplay(),
                        new Parts(allOf, anyOf, oneOf)
                );
            })
            .toList();

        return new RequirementsView(
                base.reviewMode(),
                base.assessment(),
                base.domains(),
                decoratedReqs,
                base.firedRules(),
                base.policyVersion()
        );
    }

    /* ---------- helpers ---------- */

    private List<Part> decorateList(List<Part> parts, String appId, OffsetDateTime asOf) {
        if (parts == null) return null;
        return parts.stream().map(part -> decoratePart(part, appId, asOf)).toList();
    }

    private Part decoratePart(Part part, String appId, OffsetDateTime asOf) {
        String field = part.profileField();
        if (field == null || field.isBlank()) return part;

        Integer maxAgeDays = part.maxAgeDays(); // nullable → infinite
        try {
            ReuseCandidate cand = reuseService
                    .findBestReusable(appId, field, maxAgeDays, asOf)
                    .orElse(null);
            // rebuild with reuseCandidate
            return new Part(
                    part.label(),
                    part.profileField(),
                    part.maxAgeDays(),
                    part.reviewer(),
                    part.uiOptions(),
                    cand
            );
        } catch (Exception e) {
            log.warn("Reuse decoration failed (appId={}, field={}): {}", appId, field, e.toString());
            return part; // fail-open
        }
    }

    private static OffsetDateTime parseAsOfOffset(String iso) {
        if (iso == null || iso.isBlank()) return OffsetDateTime.now(ZoneOffset.UTC);
        try { return OffsetDateTime.parse(iso); }
        catch (Exception e) { return LocalDateTime.parse(iso).atOffset(ZoneOffset.UTC); }
    }

    private static String stringOrDefault(Object v, String def) {
        return v == null ? def : Objects.toString(v);
    }
    private static boolean booleanOrDefault(Object v, boolean def) {
        if (v == null) return def;
        if (v instanceof Boolean b) return b;
        return Boolean.parseBoolean(v.toString());
    }
}
