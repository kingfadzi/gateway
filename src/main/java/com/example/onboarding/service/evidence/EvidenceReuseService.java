// src/main/java/com/example/onboarding/service/evidence/EvidenceReuseService.java
package com.example.onboarding.service.evidence;

import com.example.onboarding.dto.evidence.ReuseCandidate;
import com.example.onboarding.repository.evidence.EvidenceReuseRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * C3: server-side reuse check (read-only).
 * Selects the best reusable evidence for a given profile field using deterministic precedence rules.
 * Uses OffsetDateTime consistently across the codebase.
 */
@Service
public class EvidenceReuseService {

    private final EvidenceReuseRepository repo;

    public EvidenceReuseService(EvidenceReuseRepository repo) {
        this.repo = repo;
    }

    public Optional<ReuseCandidate> findBestReusable(String appId,
                                                     String profileField,
                                                     Integer maxAgeDays,
                                                     OffsetDateTime asOf) {
        String key = normalizeKey(profileField);
        if (key == null || key.isBlank()) return Optional.empty();

        // default to "now" in UTC if caller omits asOf
        OffsetDateTime ref = (asOf == null) ? OffsetDateTime.now(ZoneOffset.UTC) : asOf;

        return repo.findBestReusable(appId, key, maxAgeDays, ref);
    }

    /* -------- helpers -------- */
    private static String normalizeKey(String key) {
        if (key == null) return null;
        return key.trim().toLowerCase().replace('.', '_');
    }
}
