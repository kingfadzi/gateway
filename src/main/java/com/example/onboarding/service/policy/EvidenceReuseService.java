// src/main/java/com/example/onboarding/evidence/EvidenceReuseService.java
package com.example.onboarding.service.policy;

import com.example.onboarding.dto.policy.ReuseCandidate;
import com.example.onboarding.repository.policy.EvidenceReuseRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Optional;

@Service
public class EvidenceReuseService {

    private final EvidenceReuseRepository repo;

    public EvidenceReuseService(EvidenceReuseRepository repo) {
        this.repo = repo;
    }

    public Optional<ReuseCandidate> findBestReusable(String appId, String profileField, Integer maxAgeDays, OffsetDateTime asOf) {
        return repo.findBestReusable(appId, profileField, maxAgeDays, asOf);
    }
}
