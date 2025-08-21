// src/main/java/com/example/onboarding/service/evidence/EvidenceReuseService.java
package com.example.onboarding.service.evidence;

import com.example.onboarding.dto.evidence.ReuseCandidate;
import com.example.onboarding.repository.evidence.EvidenceReuseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

@Service
public class EvidenceReuseService {

    private static final Logger log = LoggerFactory.getLogger(EvidenceReuseService.class);
    private final EvidenceReuseRepository repo;

    public EvidenceReuseService(EvidenceReuseRepository repo) {
        this.repo = repo;
    }

    public Optional<ReuseCandidate> findBestReusable(String appId,
                                                     String profileField,
                                                     Integer maxAgeDays,
                                                     OffsetDateTime asOf) {
        if (profileField == null || profileField.isBlank()) return Optional.empty();

        OffsetDateTime ref = (asOf == null) ? OffsetDateTime.now(ZoneOffset.UTC) : asOf;

        // Build candidate keys in priority order:
        //  1) last dotted segment (e.g., "security.encryption_at_rest" -> "encryption_at_rest")
        //  2) dotted->underscored (e.g., "security_encryption_at_rest")
        //  3) original (normalized)
        Set<String> keys = new LinkedHashSet<>();

        String trimmed = profileField.trim();
        String lastSeg = trimmed.contains(".")
                ? trimmed.substring(trimmed.lastIndexOf('.') + 1)
                : trimmed;

        keys.add(normalize(lastSeg));
        keys.add(normalize(trimmed.replace('.', '_')));
        keys.add(normalize(trimmed));

        for (String k : keys) {
            Optional<ReuseCandidate> hit = repo.findBestReusable(appId, k, maxAgeDays, ref);
            if (hit.isPresent()) {
                if (log.isDebugEnabled()) log.debug("Reuse hit: appId={}, key={}, asOf={}, maxAgeDays={}", appId, k, ref, maxAgeDays);
                return hit;
            } else {
                if (log.isDebugEnabled()) log.debug("Reuse miss: appId={}, key={}, asOf={}, maxAgeDays={}", appId, k, ref, maxAgeDays);
            }
        }
        return Optional.empty();
    }

    private static String normalize(String key) {
        return key == null ? null : key.trim().toLowerCase();
    }
}
