// src/main/java/com/example/onboarding/repository/claims/ClaimsRepository.java
package com.example.gateway.claims.repository;

import java.time.OffsetDateTime;

public interface ClaimsRepository {
  String insertClaim(String appId, String releaseId, String requirementId,
                     String profileFieldKey, String typeExpected, String evidenceId,
                     OffsetDateTime asOf, String method);
}
