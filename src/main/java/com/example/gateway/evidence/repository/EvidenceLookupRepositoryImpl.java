// src/main/java/com/example/onboarding/repository/evidence/impl/EvidenceLookupRepositoryImpl.java
package com.example.gateway.evidence.repository;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Repository
public class EvidenceLookupRepositoryImpl implements EvidenceLookupRepository {

  private final NamedParameterJdbcTemplate jdbc;

  public EvidenceLookupRepositoryImpl(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public EvidenceRow findById(String evidenceId) {
    var sql = """
      SELECT e.evidence_id,
             pf.app_id,
             pf.field_key AS profile_field_key,
             e.type,
             e.uri,
             e.method,
             e.confidence,
             e.sha256,                 -- NEW
             e.source_system,          -- NEW
             e.valid_from,
             e.valid_until,
             e.revoked_at,
             e.created_at
      FROM evidence e
      JOIN profile_field pf ON pf.id = e.profile_field_id
      WHERE e.evidence_id = :evid
    """;
    try {
      return jdbc.queryForObject(sql, Map.of("evid", evidenceId), (rs, i) ->
          new EvidenceRow(
              rs.getString("evidence_id"),
              rs.getString("app_id"),
              rs.getString("profile_field_key"),
              rs.getString("type"),
              rs.getString("uri"),
              rs.getString("method"),
              rs.getString("confidence"),
              rs.getString("sha256"),          // NEW
              rs.getString("source_system"),   // NEW
              rs.getObject("valid_from", java.time.OffsetDateTime.class),
              rs.getObject("valid_until", java.time.OffsetDateTime.class),
              rs.getObject("revoked_at", java.time.OffsetDateTime.class),
              rs.getObject("created_at", java.time.OffsetDateTime.class)
          )
      );
    } catch (EmptyResultDataAccessException e) {
      return null;
    }
  }
}
