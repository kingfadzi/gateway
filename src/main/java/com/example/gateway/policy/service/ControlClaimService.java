package com.example.gateway.policy.service;

import com.example.gateway.claims.dto.ClaimDto;
import com.example.gateway.policy.dto.CreateClaimRequest;
import com.example.gateway.policy.repository.ControlClaimRepository;
import com.example.gateway.evidence.repository.EvidenceReuseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ControlClaimService {

    private final ControlClaimRepository repo;
    private final EvidenceReuseRepository evidenceRepo;
    private final ObjectMapper om;

    public ControlClaimService(ControlClaimRepository repo,
                               EvidenceReuseRepository evidenceRepo,
                               ObjectMapper om) {
        this.repo = repo;
        this.evidenceRepo = evidenceRepo;
        this.om = om; // Spring Boot auto-configures this
    }

    public ClaimDto createClaim(String appId, CreateClaimRequest req) {
        final List<String> reasons = new ArrayList<>();
        boolean acceptable = true;
        final OffsetDateTime now = OffsetDateTime.now();
        final String method = (req.method() == null || req.method().isBlank()) ? "manual" : req.method();
        final OffsetDateTime ref = (req.releaseWindowStart() != null) ? req.releaseWindowStart() : now;

        // 1) Load evidence with owning app + field metadata
        var evOpt = evidenceRepo.findEvidenceForClaim(req.evidenceId());
        if (evOpt.isEmpty()) {
            acceptable = false;
            reasons.add("evidence_not_found");

            ObjectNode decision = baseDecision(om, acceptable, reasons, now, ref, appId, req, method);
            // evidence block omitted because we couldn't load it
            return repo.insertSubmitted(appId, req.requirementId(), req.releaseId(),
                    req.evidenceId(), method, acceptable, reasons, now, decision);
        }

        var ev = evOpt.get();

        // 2) Ownership check
        if (!appId.equals(ev.appId())) {
            acceptable = false;
            reasons.add("wrong_app:expected=" + appId + ",actual=" + ev.appId());
        }

        // 3) Expectation checks
        if (req.profileFieldExpected() != null && !req.profileFieldExpected().isBlank()) {
            if (!req.profileFieldExpected().equals(ev.profileFieldKey())) {
                acceptable = false;
                reasons.add("field_mismatch:expected=" + req.profileFieldExpected() + ",actual=" + ev.profileFieldKey());
            }
        }
        if (req.typeExpected() != null && !req.typeExpected().isBlank()) {
            if (ev.type() == null || !req.typeExpected().equalsIgnoreCase(ev.type())) {
                acceptable = false;
                reasons.add("type_mismatch:expected=" + req.typeExpected() + ",actual=" + String.valueOf(ev.type()));
            }
        }

        // 4) Lifecycle / validity
        if (ev.revokedAt() != null) {
            acceptable = false;
            reasons.add("revoked_at=" + ev.revokedAt());
        }
        if (ev.validFrom() != null && ev.validFrom().isAfter(ref)) {
            acceptable = false;
            reasons.add("not_yet_valid:valid_from=" + ev.validFrom() + ",ref=" + ref);
        }
        if (ev.validUntil() != null && ev.validUntil().isBefore(ref)) {
            acceptable = false;
            reasons.add("expired:valid_until=" + ev.validUntil() + ",ref=" + ref);
        }

        // 5) Build decision_json
        ObjectNode decision = baseDecision(om, acceptable, reasons, now, ref, appId, req, method);
        ObjectNode evidenceNode = om.createObjectNode();
        evidenceNode.put("evidenceId", ev.evidenceId());
        evidenceNode.put("profileFieldId", ev.profileFieldId());
        evidenceNode.put("profileFieldKey", ev.profileFieldKey());
        evidenceNode.put("type", ev.type());
        evidenceNode.put("uri", ev.uri());
        if (ev.validFrom() != null)  evidenceNode.put("validFrom", ev.validFrom().toString());
        if (ev.validUntil() != null) evidenceNode.put("validUntil", ev.validUntil().toString());
        if (ev.revokedAt() != null)  evidenceNode.put("revokedAt", ev.revokedAt().toString());
        decision.set("evidence", evidenceNode);

        // 6) Persist
        return repo.insertSubmitted(appId, req.requirementId(), req.releaseId(),
                ev.evidenceId(), method, acceptable, reasons, now, decision);
    }

    /* ---------------- helpers ---------------- */

    private static ObjectNode baseDecision(ObjectMapper om,
                                           boolean acceptable,
                                           List<String> reasons,
                                           OffsetDateTime checkedAt,
                                           OffsetDateTime referenceTime,
                                           String appId,
                                           CreateClaimRequest req,
                                           String method) {
        ObjectNode node = om.createObjectNode();
        node.put("acceptable", acceptable);
        node.putPOJO("reasons", reasons);
        node.put("checkedAt", checkedAt.toString());
        node.put("referenceTime", referenceTime.toString());
        node.put("appId", appId);
        node.put("requirementId", req.requirementId());
        node.put("releaseId", req.releaseId());
        node.put("evidenceId", req.evidenceId());
        node.put("method", method);
        node.put("status", "submitted");
        node.put("confidence", confidenceFromMethod(method));
        return node;
    }

    private static String confidenceFromMethod(String method) {
        String m = method.toLowerCase();
        if ("auto".equals(m)) return "high";
        if ("imported".equals(m)) return "medium";
        return "low"; // manual
    }
}
