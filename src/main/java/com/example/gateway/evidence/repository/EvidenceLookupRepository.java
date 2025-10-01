// src/main/java/com/example/onboarding/repository/evidence/EvidenceLookupRepository.java
package com.example.gateway.evidence.repository;

import java.time.OffsetDateTime;

public interface EvidenceLookupRepository {
  EvidenceRow findById(String evidenceId);

  record EvidenceRow(
      String evidenceId,
      String appId,
      String profileFieldKey, // stored (underscore) key
      String type,            // link/file/...
      String uri,
      String method,          // human_verified/system_verified/self_attested
      String confidence,      // high/medium/low/null
      String sha256,
      String sourceSystem,
      OffsetDateTime validFrom,
      OffsetDateTime validUntil,
      OffsetDateTime revokedAt,
      OffsetDateTime createdAt
  ) {}
}
