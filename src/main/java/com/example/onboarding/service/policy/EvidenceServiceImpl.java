package com.example.onboarding.service.policy;

import com.example.onboarding.dto.evidence.CreateEvidenceRequest;
import com.example.onboarding.dto.evidence.EvidenceDto;
import com.example.onboarding.repository.policy.EvidenceRepository;
import com.example.onboarding.repository.policy.ProfileFieldLookupRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class EvidenceServiceImpl implements EvidenceService {

    private final EvidenceRepository evidenceRepo;
    private final ProfileFieldLookupRepository pfLookup;

    public EvidenceServiceImpl(EvidenceRepository evidenceRepo, ProfileFieldLookupRepository pfLookup) {
        this.evidenceRepo = evidenceRepo;
        this.pfLookup = pfLookup;
    }

    @Override
    public EvidenceDto createOrDedup(String appId, CreateEvidenceRequest req, MultipartFile file) throws Exception {
        // 1) Resolve profile_field_id
        String profileFieldId = resolveProfileFieldId(appId, req);
        if (profileFieldId == null) {
            throw new IllegalArgumentException("profileFieldId or resolvable fieldKey is required");
        }

        // 2) Determine URI + SHA256 source
        final boolean hasFile = (file != null && !file.isEmpty());
        final String uri = normalizeUri(req.uri(), file);
        final String sha256 = computeSha256Hex(hasFile ? file.getBytes()
                                                       : uri.getBytes(StandardCharsets.UTF_8));

        // 3) Dedup by (profile_field_id, sha256) then (profile_field_id, uri)
        Optional<EvidenceDto> bySha = evidenceRepo.findByProfileFieldAndSha(profileFieldId, sha256);
        if (bySha.isPresent()) return bySha.get();

        Optional<EvidenceDto> byUri = evidenceRepo.findByProfileFieldAndUri(profileFieldId, uri);
        if (byUri.isPresent()) return byUri.get();

        // 4) Insert immutable evidence
        String evidenceId = "ev_" + UUID.randomUUID().toString().replace("-", "");
        return evidenceRepo.insert(
                evidenceId,
                profileFieldId,
                uri,
                req.type(),
                sha256,
                null,      // source_system (optional)
                null       // submitted_by (optional)
        );
    }

    @Override
    public EvidenceDto get(String evidenceId) {
        return evidenceRepo.findById(evidenceId)
                .orElseThrow(() -> new IllegalArgumentException("Evidence not found: " + evidenceId));
    }

    /* ----------------- helpers ----------------- */

    private String resolveProfileFieldId(String appId, CreateEvidenceRequest req) {
        if (req.profileFieldId() != null && !req.profileFieldId().isBlank()) {
            return req.profileFieldId();
        }
        if (req.fieldKey() != null && !req.fieldKey().isBlank()) {
            return pfLookup.resolveProfileFieldIdForAppAndKey(appId, req.fieldKey()).orElse(null);
        }
        return null;
    }

    private static String normalizeUri(String providedUri, MultipartFile file) {
        if (providedUri != null && !providedUri.isBlank()) return providedUri;
        if (file != null && !file.isEmpty()) {
            // Satisfy NOT NULL URI while keeping a deterministic reference
            var name = Objects.toString(file.getOriginalFilename(), "upload");
            return "file:" + name;
        }
        throw new IllegalArgumentException("uri is required (or provide a file to derive a synthetic file: URI)");
    }

    private static String computeSha256Hex(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(data);
        return HexFormat.of().formatHex(digest);
    }
}
