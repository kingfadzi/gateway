package com.example.onboarding.service.policy;

import com.example.onboarding.dto.evidence.CreateEvidenceRequest;
import com.example.onboarding.dto.evidence.EvidenceDto;
import com.example.onboarding.repository.evidence.EvidenceRepository;
import com.example.onboarding.service.evidence.EvidenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

@Service
public class EvidenceServiceImpl implements EvidenceService {

    private static final Logger log = LoggerFactory.getLogger(EvidenceServiceImpl.class);
    private final EvidenceRepository repo;

    public EvidenceServiceImpl(EvidenceRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional
    public EvidenceDto createOrDedup(String appId, CreateEvidenceRequest req, MultipartFile file) throws Exception {
        if (req == null) throw new IllegalArgumentException("Request body is required");
        if (req.profileField() == null || req.profileField().isBlank()) {
            throw new IllegalArgumentException("profileField is required");
        }

        // 1) Resolve profile_field_id for (appId, latest profile) + field key
        final String profileFieldId;
        if (req.profileFieldId() != null && !req.profileFieldId().isBlank()) {
            profileFieldId = req.profileFieldId().trim();
        } else if (req.profileField() != null && !req.profileField().isBlank()) {
            profileFieldId = repo.resolveProfileFieldId(appId, req.profileField().trim())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown profileField for app: " + appId + " :: " + req.profileField()));
        } else {
            throw new IllegalArgumentException("Provide either profileFieldId or fieldKey/profileField");
        }

        // 2) Determine content identity (URI or file) and compute sha256
        final String sha256;
        final String uri;
        if (file != null) {
            byte[] bytes = file.getBytes();
            sha256 = sha256(bytes);
            uri = (req.uri() != null && !req.uri().isBlank())
                    ? req.uri().trim()
                    : "file://" + sha256 + "/" + (file.getOriginalFilename() == null ? "upload.bin" : file.getOriginalFilename());
        } else if (req.uri() != null && !req.uri().isBlank()) {
            uri = req.uri().trim();
            // simple, deterministic hash for link evidence
            sha256 = sha256(uri.getBytes(StandardCharsets.UTF_8));
        } else {
            throw new IllegalArgumentException("Either 'uri' (for link evidence) or 'file' (multipart) must be provided");
        }

        // 3) Cross-app dedup *within the app* by sha256
        Optional<Map<String, Object>> existingBySha = repo.findExistingInAppBySha(appId, sha256);
        if (existingBySha.isPresent()) {
            log.debug("Dedup (sha) hit for app={} sha256={}", appId, sha256);
            return repo.rowToDto(existingBySha.get());
        }

        // 4) Insert (idempotent on (profile_field_id, uri)); if conflict, fetch existing
        OffsetDateTime now = OffsetDateTime.now();
        Optional<Map<String, Object>> inserted = repo.insertIfNotExists(
                profileFieldId,
                uri,
                safe(req.type()),
                sha256,
                safe(req.sourceSystem()),
                req.validFrom(),
                req.validUntil(),
                now
        );

        Map<String, Object> row = inserted.orElseGet(() -> repo.getByProfileFieldAndUri(profileFieldId, uri));
        return repo.rowToDto(row);
    }

    @Override
    @Transactional(readOnly = true)
    public EvidenceDto get(String evidenceId) {
        return repo.getById(evidenceId);
    }

    /* ---------------- helpers ---------------- */

    private static String sha256(byte[] bytes) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(md.digest(bytes));
    }

    private static String safe(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
