// src/main/java/com/example/onboarding/service/claims/ClaimsService.java
package com.example.gateway.claims.service;

import com.example.gateway.claims.dto.ClaimDecision;
import com.example.gateway.claims.dto.CreateClaimRequest;
import com.example.gateway.requirements.dto.RequirementsView;
import com.example.gateway.requirements.dto.RequirementsView.Part;
import com.example.gateway.evidence.dto.ReuseCandidate;
import com.example.gateway.evidence.repository.EvidenceLookupRepository;
import com.example.gateway.claims.repository.ClaimsRepository;
import com.example.gateway.requirements.service.RequirementsService;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class ClaimsService {

  private final RequirementsService requirementsService;
  private final EvidenceLookupRepository evidenceRepo;
  private final ClaimsRepository claimsRepo;

  public ClaimsService(RequirementsService requirementsService,
                       EvidenceLookupRepository evidenceRepo,
                       ClaimsRepository claimsRepo) {
    this.requirementsService = requirementsService;
    this.evidenceRepo = evidenceRepo;
    this.claimsRepo = claimsRepo;
  }

  public ClaimDecision evaluateAndCreate(String appId, CreateClaimRequest req) {
    String releaseId = req.releaseId();
    String requirementId = req.requirementId();
    String fieldExp = normalizeKey(req.profileFieldExpected());
    String typeExp = emptyToNull(req.typeExpected());
    String evidenceId = req.evidenceId();
    boolean dryRun = Boolean.TRUE.equals(req.dryRun());
    OffsetDateTime asOf = parseAsOf(req.releaseWindowStartIso());

    List<String> reasons = new ArrayList<>();

    // 1) Load requirement parts (to find the target part and its constraints)
    RequirementsView view = requirementsService.getRequirements(appId, releaseId, req.releaseWindowStartIso());
    Part targetPart = findPart(view, requirementId, fieldExp);
    if (targetPart == null) {
      reasons.add("requirement_part_not_found");
      return new ClaimDecision(false, reasons, false, null, null);
    }
    Integer maxAgeDays = targetPart.maxAgeDays();
    if (typeExp == null && targetPart != null) {
      typeExp = firstNonNull(typeExp, guessTypeFromUiOptions(targetPart));
    }

    // 2) Load evidence
    var ev = evidenceRepo.findById(evidenceId);
    if (ev == null) {
      reasons.add("evidence_not_found");
      return new ClaimDecision(false, reasons, false, null, null);
    }

    // 3) Validate evidence
    if (!Objects.equals(ev.appId(), appId)) reasons.add("wrong_app");
    if (!Objects.equals(normalizeKey(ev.profileFieldKey()), fieldExp)) reasons.add("field_mismatch");
    if (typeExp != null && !typeExp.equalsIgnoreCase(ev.type())) reasons.add("type_mismatch");
    if (ev.revokedAt() != null) reasons.add("revoked");

    if (ev.validFrom() == null) reasons.add("missing_valid_from");
    else if (ev.validFrom().isAfter(asOf)) reasons.add("not_valid_yet");

    if (ev.validUntil() != null && ev.validUntil().isBefore(asOf)) reasons.add("expired");

    if (maxAgeDays != null && ev.validFrom() != null) {
      OffsetDateTime cutoff = asOf.minusDays(maxAgeDays);
      if (ev.validFrom().isBefore(cutoff)) reasons.add("too_old_for_max_age");
    }

    boolean ok = reasons.isEmpty();
    if (!ok) return new ClaimDecision(false, reasons, false, null, null);

    // 4) Persist (unless dry-run)
    String claimId = null;
    if (!dryRun) {
      claimId = claimsRepo.insertClaim(
          appId, releaseId, requirementId, fieldExp, typeExp, evidenceId, asOf, emptyToNull(req.method())
      );
    }

    // 5) Build appliedEvidence (reuse the evidence row to map to ReuseCandidate-like view)
    ReuseCandidate applied = new ReuseCandidate(
                    ev.evidenceId(),
                    ev.validFrom(),          // valid_from
                    ev.validUntil(),         // valid_until
                    ev.confidence(),
                    ev.method(),
                    ev.uri(),
                    null,                    // sha256 (add to repo if you want it)
                    ev.type(),
                    null,                    // source_system (add to repo if you want it)
                    ev.createdAt()           // created_at
            );

    return new ClaimDecision(true, List.of(), !dryRun, claimId, applied);
  }

  /* ---------- helpers ---------- */

  private static String normalizeKey(String key) {
    if (key == null) return null;
    return key.trim().toLowerCase().replace('.', '_');
  }

  private static String emptyToNull(String s) {
    return (s == null || s.isBlank()) ? null : s;
  }

  private static OffsetDateTime parseAsOf(String iso) {
    if (iso == null || iso.isBlank()) return OffsetDateTime.now(ZoneOffset.UTC);
    try { return OffsetDateTime.parse(iso); }
    catch (Exception e) { return LocalDateTime.parse(iso).atOffset(ZoneOffset.UTC); }
  }

  private static Part findPart(RequirementsView view, String requirementId, String fieldKey) {
    if (view == null || view.requirements() == null) return null;
    for (var req : view.requirements()) {
      if (!Objects.equals(req.id(), requirementId)) continue;
      var p = req.parts();
      var all = concat(p.allOf(), p.anyOf(), p.oneOf());
      for (var part : all) {
        if (Objects.equals(normalizeKey(part.profileField()), fieldKey)) return part;
      }
    }
    return null;
  }

  private static java.util.List<Part> concat(java.util.List<Part> a, java.util.List<Part> b, java.util.List<Part> c) {
    java.util.List<Part> out = new java.util.ArrayList<>();
    if (a != null) out.addAll(a);
    if (b != null) out.addAll(b);
    if (c != null) out.addAll(c);
    return out;
  }

  private static String guessTypeFromUiOptions(Part part) {
    // Simple heuristic; adjust if you encode expected type in UI options
    if (part.uiOptions() != null && part.uiOptions().stream().anyMatch(s -> s.toLowerCase().contains("link"))) return "link";
    if (part.uiOptions() != null && part.uiOptions().stream().anyMatch(s -> s.toLowerCase().contains("file"))) return "file";
    return null;
  }

  private static <T> T firstNonNull(T a, T b) { return a != null ? a : b; }
}
