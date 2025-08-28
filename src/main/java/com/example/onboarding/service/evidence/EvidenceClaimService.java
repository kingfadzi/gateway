package com.example.onboarding.service.evidence;

import com.example.onboarding.dto.claims.AttachEvidenceRequest;
import com.example.onboarding.dto.evidence.Evidence;
import com.example.onboarding.repository.evidence.EvidenceRepository;
import com.example.onboarding.repository.policy.ControlClaimRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class EvidenceClaimService {
    
    private static final Logger log = LoggerFactory.getLogger(EvidenceClaimService.class);
    
    private final NamedParameterJdbcTemplate jdbc;
    private final EvidenceRepository evidenceRepository;
    private final ControlClaimRepository claimRepository;
    
    public EvidenceClaimService(NamedParameterJdbcTemplate jdbc, 
                               EvidenceRepository evidenceRepository,
                               ControlClaimRepository claimRepository) {
        this.jdbc = jdbc;
        this.evidenceRepository = evidenceRepository;
        this.claimRepository = claimRepository;
    }
    
    /**
     * Attach evidence to a claim with optional document references
     */
    @Transactional
    public Evidence attachEvidenceToClaim(String claimId, String evidenceId, AttachEvidenceRequest request) {
        // Validate claim exists
        var claim = claimRepository.findClaimWithEvidence(claimId)
            .orElseThrow(() -> new IllegalArgumentException("Claim not found: " + claimId));
        
        // Validate evidence exists and get current state
        Evidence evidence = evidenceRepository.findEvidenceById(evidenceId)
            .orElseThrow(() -> new IllegalArgumentException("Evidence not found: " + evidenceId));
        
        // Validate same app_id (this will be enforced by database triggers too)
        if (!claim.appId().equals(evidence.appId())) {
            throw new IllegalArgumentException(
                String.format("Evidence app (%s) must match claim app (%s)", 
                    evidence.appId(), claim.appId())
            );
        }
        
        // Note: We can't directly compare field keys since evidence has profileFieldId, not key
        // The database foreign key constraint will ensure referential integrity
        
        // Update evidence to reference the claim and optional document
        String sql = """
            UPDATE evidence 
            SET claim_id = :claimId,
                document_id = :documentId,
                doc_version_id = :docVersionId,
                updated_at = now()
            WHERE evidence_id = :evidenceId
            """;
        
        int updated = jdbc.update(sql, new MapSqlParameterSource()
            .addValue("claimId", claimId)
            .addValue("documentId", request.documentId())
            .addValue("docVersionId", request.docVersionId())
            .addValue("evidenceId", evidenceId)
        );
        
        if (updated == 0) {
            throw new RuntimeException("Failed to attach evidence to claim");
        }
        
        log.info("Attached evidence {} to claim {} with document {}:{}", 
            evidenceId, claimId, request.documentId(), request.docVersionId());
        
        // Return updated evidence
        return evidenceRepository.findEvidenceById(evidenceId)
            .orElseThrow(() -> new RuntimeException("Failed to retrieve updated evidence"));
    }
    
    /**
     * Detach evidence from a claim
     */
    @Transactional
    public Evidence detachEvidenceFromClaim(String evidenceId) {
        // Validate evidence exists
        Evidence evidence = evidenceRepository.findEvidenceById(evidenceId)
            .orElseThrow(() -> new IllegalArgumentException("Evidence not found: " + evidenceId));
        
        // Update evidence to remove claim reference
        String sql = """
            UPDATE evidence 
            SET claim_id = NULL,
                updated_at = now()
            WHERE evidence_id = :evidenceId
            """;
        
        int updated = jdbc.update(sql, new MapSqlParameterSource()
            .addValue("evidenceId", evidenceId)
        );
        
        if (updated == 0) {
            throw new RuntimeException("Failed to detach evidence from claim");
        }
        
        log.info("Detached evidence {} from claim", evidenceId);
        
        // Return updated evidence
        return evidenceRepository.findEvidenceById(evidenceId)
            .orElseThrow(() -> new RuntimeException("Failed to retrieve updated evidence"));
    }
    
}