// src/main/java/com/example/onboarding/repository/claims/impl/ClaimsRepositoryImpl.java
package com.example.gateway.claims.repository;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Repository
public class ClaimsRepositoryImpl implements ClaimsRepository {

  private final NamedParameterJdbcTemplate jdbc;

  public ClaimsRepositoryImpl(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public String insertClaim(String appId, String releaseId, String requirementId,
                            String profileFieldKey, String typeExpected, String evidenceId,
                            OffsetDateTime asOf, String method) {

    String claimId = "clm_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

    var sql = """
      INSERT INTO control_claim
        (claim_id, app_id, release_id, requirement_id, profile_field_key, type_expected,
         evidence_id, method, as_of, status, created_at)
      VALUES
        (:claim_id, :app_id, :release_id, :requirement_id, :profile_field_key, :type_expected,
         :evidence_id, :method, :as_of, 'submitted', now())
    """;

    jdbc.update(sql, Map.of(
        "claim_id", claimId,
        "app_id", appId,
        "release_id", releaseId,
        "requirement_id", requirementId,
        "profile_field_key", profileFieldKey,
        "type_expected", typeExpected,
        "evidence_id", evidenceId,
        "method", method == null ? "manual" : method,
        "as_of", asOf
    ));

    return claimId;
  }
}
