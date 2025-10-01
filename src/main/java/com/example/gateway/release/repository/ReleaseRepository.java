// com.example.onboarding.repo.ReleaseRepository
package com.example.gateway.release.repository;

import java.time.Instant;
import java.util.Optional;

public interface ReleaseRepository {
    Optional<Instant> findWindowStart(String appId, String releaseId);
}
