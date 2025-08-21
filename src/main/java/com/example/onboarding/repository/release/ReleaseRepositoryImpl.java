package com.example.onboarding.repository.release;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

// com.example.onboarding.repo.impl.ReleaseRepositoryImpl
@Repository
public class ReleaseRepositoryImpl implements ReleaseRepository {
  private final NamedParameterJdbcTemplate jdbc;
  public ReleaseRepositoryImpl(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

  @Override
  public Optional<Instant> findWindowStart(String appId, String releaseId) {
    var sql = """
      SELECT window_start FROM release
      WHERE app_id = :appId AND release_id = :releaseId
    """;
    var p = Map.of("appId", appId, "releaseId", releaseId);
    try {
      OffsetDateTime odt = jdbc.queryForObject(sql, p, (rs, i) -> rs.getObject("window_start", OffsetDateTime.class));
      return Optional.ofNullable(odt).map(OffsetDateTime::toInstant);
    } catch (EmptyResultDataAccessException e) {
      return Optional.empty();
    }
  }
}
