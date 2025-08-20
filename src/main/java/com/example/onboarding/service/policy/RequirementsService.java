package com.example.onboarding.service.policy;

import com.example.onboarding.dto.policy.OpaRequest;
import com.example.onboarding.dto.policy.PolicyDecision;
import com.example.onboarding.dto.policy.PolicyInput;
import com.example.onboarding.dto.policy.RequirementsView;
import com.example.onboarding.integrations.OpaClient;
import com.example.onboarding.repository.evidence.EvidenceReuseRepository; // used to read profile context
import com.example.onboarding.repository.policy.RequirementsMapper;     // static utility
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;

@Service
public class RequirementsService {

    private final OpaClient opaClient;
    private final EvidenceReuseRepository profileReader;

    public RequirementsService(OpaClient opaClient,
                               EvidenceReuseRepository profileReader) {
        this.opaClient = opaClient;
        this.profileReader = profileReader;
    }

    /** C2: read-only; build OPA input from stored profile; map to FE view (no reuse decoration yet). */
    public RequirementsView getRequirements(String appId, String releaseId, String releaseWindowStartIso) {
        // 1) Read normalized policy context from profile/profile_field
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

        // 3) Call OPA
        PolicyDecision decision = opaClient.evaluate(new OpaRequest(input));

        // 4) Map using your static mapper signature: (PolicyDecision, releaseWindowStartIso)
        return RequirementsMapper.toView(decision, releaseWindowStartIso);
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
