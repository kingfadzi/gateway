package com.example.onboarding.service.evidence;

import com.example.onboarding.dto.evidence.CreateEvidenceRequest;
import com.example.onboarding.dto.evidence.Evidence;
import com.example.onboarding.repository.evidence.EvidenceRepository;
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

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import com.example.onboarding.dto.evidence.ReviewEvidenceRequest;
import com.example.onboarding.dto.evidence.ReviewEvidenceResponse;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Timestamp;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneOffset;
import java.util.*;


@Service
public class EvidenceServiceImpl implements EvidenceService {

    private static final Logger log = LoggerFactory.getLogger(EvidenceServiceImpl.class);
    private final EvidenceRepository repo;
    private final NamedParameterJdbcTemplate jdbc;

    public EvidenceServiceImpl(NamedParameterJdbcTemplate jdbc, EvidenceRepository repo) {
        this.jdbc = jdbc;
        this.repo = repo;
    }

    @Override
    @Transactional
    public Evidence createOrDedup(String appId, CreateEvidenceRequest req, MultipartFile file) throws Exception {
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
    public Evidence get(String evidenceId) {
        return repo.getById(evidenceId);
    }

    @Override
    @Transactional
    public ReviewEvidenceResponse review(String evidenceId, ReviewEvidenceRequest req) {
        String action = (req.action() == null ? "" : req.action().trim().toLowerCase(Locale.ROOT));
        return switch (action) {
            case "approve" -> approveEvidence(evidenceId, req.reviewerId());
            case "reject"  -> rejectEvidence(evidenceId, req.reviewerId());
            default -> throw new IllegalArgumentException("action must be 'approve' or 'reject'");
        };
    }

    private ReviewEvidenceResponse approveEvidence(String evidenceId, String reviewerId) {
        // 1) Lock target evidence and fetch its profile_field_id + valid_from
        var head = repo.lockHeadById(evidenceId)
                .orElseThrow(() -> new IllegalArgumentException("Evidence not found: " + evidenceId));

        String profileFieldId = head.profileFieldId();
        OffsetDateTime newValidFrom = head.validFrom() != null ? head.validFrom() : OffsetDateTime.now(ZoneOffset.UTC);


        // 2) Lock & find other active evidence for same field to supersede
        List<String> supersededIds = jdbc.query("""
        SELECT evidence_id
        FROM evidence
        WHERE profile_field_id = :pf
          AND status = 'active'
          AND evidence_id <> :id
        FOR UPDATE
    """, new MapSqlParameterSource()
                        .addValue("pf", profileFieldId)
                        .addValue("id", evidenceId),
                (rs, i) -> rs.getString("evidence_id"));

        if (!supersededIds.isEmpty()) {
            jdbc.update("""
            UPDATE evidence
            SET status = 'superseded',
                valid_until = :until,
                updated_at = now()
            WHERE evidence_id IN (:ids)
        """, new MapSqlParameterSource()
                    .addValue("until", Timestamp.from(newValidFrom.toInstant()))
                    .addValue("ids", supersededIds));
        }

        // 3) Approve target
        jdbc.update("""
        UPDATE evidence
        SET status = 'active',
            reviewed_by = :rb,
            reviewed_at = now(),
            updated_at = now()
        WHERE evidence_id = :id
    """, new MapSqlParameterSource()
                .addValue("rb", reviewerId)
                .addValue("id", evidenceId));

        return new ReviewEvidenceResponse(
                evidenceId,
                "active",
                OffsetDateTime.now(ZoneOffset.UTC),
                reviewerId,
                supersededIds
        );
    }

    private ReviewEvidenceResponse rejectEvidence(String evidenceId, String reviewerId) {
        // Lock row (ensures we don't race with a parallel approval)
        repo.lockHeadById(evidenceId)
                .orElseThrow(() -> new IllegalArgumentException("Evidence not found: " + evidenceId));


        jdbc.update("""
        UPDATE evidence
        SET status = 'revoked',
            revoked_at = now(),
            reviewed_by = :rb,
            reviewed_at = now(),
            updated_at = now()
        WHERE evidence_id = :id
    """, new MapSqlParameterSource()
                .addValue("rb", reviewerId)
                .addValue("id", evidenceId));

        return new ReviewEvidenceResponse(
                evidenceId,
                "revoked",
                OffsetDateTime.now(ZoneOffset.UTC),
                reviewerId,
                List.of()
        );
    }

    @Override
    public List<Evidence> listByAppAndField(String appId, String fieldKey) {
        var params = new MapSqlParameterSource()
                .addValue("appId", appId)
                .addValue("fieldKey", fieldKey);

        return jdbc.query("""
        SELECT
            e.*,
            pf.field_key AS profile_field_key
        FROM evidence e
        JOIN profile_field pf ON pf.id         = e.profile_field_id
        JOIN profile       p  ON p.profile_id  = pf.profile_id
        JOIN application   a  ON a.app_id      = p.scope_id
                             AND p.scope_type  = 'application'
        WHERE a.app_id     = :appId
          AND pf.field_key = :fieldKey
        ORDER BY
          CASE e.status WHEN 'active' THEN 0 WHEN 'superseded' THEN 1 ELSE 2 END,
          COALESCE(e.valid_from, e.created_at) DESC
    """, params, evidenceRowMapper());
    }


// ---------- helpers (add once if you don't have them) ----------

    private static RowMapper<Evidence> evidenceRowMapper() {
        return new RowMapper<>() {
            @Override public Evidence mapRow(ResultSet rs, int rowNum) throws SQLException {
                return new Evidence(
                        rs.getString("evidence_id"),
                        rs.getString("profile_field_id"),
                        // ensure your query includes: pf.key AS profile_field_key
                        getNullableString(rs, "profile_field_key"),

                        rs.getString("uri"),
                        rs.getString("type"),
                        rs.getString("sha256"),
                        rs.getString("source_system"),
                        getNullableString(rs, "submitted_by"),

                        rs.getString("status"),
                        rs.getObject("valid_from",  java.time.OffsetDateTime.class),
                        rs.getObject("valid_until", java.time.OffsetDateTime.class),
                        rs.getObject("revoked_at",  java.time.OffsetDateTime.class),

                        getNullableString(rs, "reviewed_by"),
                        rs.getObject("reviewed_at", java.time.OffsetDateTime.class),
                        getNullableString(rs, "tags"),

                        // keep legacy for back-compat; ok if null / absent
                        rs.getObject("added_at",    java.time.OffsetDateTime.class),
                        rs.getObject("created_at",  java.time.OffsetDateTime.class),
                        rs.getObject("updated_at",  java.time.OffsetDateTime.class)
                );
            }
        };
    }

    private static String getNullableString(ResultSet rs, String col) throws SQLException {
        try {
            String v = rs.getString(col);
            return (v == null || rs.wasNull()) ? null : v;
        } catch (SQLException e) {
            // Column might not be selected in some queries; treat as null
            return null;
        }
    }


    private static OffsetDateTime odt(ResultSet rs, String col) throws SQLException {
        Timestamp ts = rs.getTimestamp(col);
        return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
    }
    private static OffsetDateTime toOdt(Timestamp ts) {
        return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
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
