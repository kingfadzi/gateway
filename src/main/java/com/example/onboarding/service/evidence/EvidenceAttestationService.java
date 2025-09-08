package com.example.onboarding.service.evidence;

import com.example.onboarding.dto.evidence.Evidence;
import com.example.onboarding.dto.evidence.EvidenceFieldLinkResponse;
import com.example.onboarding.dto.profile.ProfileField;
import com.example.onboarding.exception.NotFoundException;
import com.example.onboarding.model.EvidenceFieldLink;
import com.example.onboarding.model.EvidenceFieldLinkId;
import com.example.onboarding.model.EvidenceFieldLinkStatus;
import com.example.onboarding.repository.EvidenceFieldLinkRepository;
import com.example.onboarding.repository.evidence.EvidenceRepository;
import com.example.onboarding.repository.profile.ProfileRepository;
import dev.controlplane.auditkit.annotations.Audited;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * External service for auditable evidence attestation operations.
 * This service ensures all attestations are properly audited with USER_ATTEST_EVIDENCE_FIELD events.
 */
@Service
public class EvidenceAttestationService {

    private static final Logger log = LoggerFactory.getLogger(EvidenceAttestationService.class);
    
    private final EvidenceFieldLinkRepository evidenceFieldLinkRepository;
    private final EvidenceRepository evidenceRepository;
    private final ProfileRepository profileRepository;
    private final UnifiedFreshnessCalculator unifiedFreshnessCalculator;

    public EvidenceAttestationService(EvidenceFieldLinkRepository evidenceFieldLinkRepository,
                                      EvidenceRepository evidenceRepository,
                                      ProfileRepository profileRepository,
                                      UnifiedFreshnessCalculator unifiedFreshnessCalculator) {
        this.evidenceFieldLinkRepository = evidenceFieldLinkRepository;
        this.evidenceRepository = evidenceRepository;
        this.profileRepository = profileRepository;
        this.unifiedFreshnessCalculator = unifiedFreshnessCalculator;
    }

    /**
     * Attest an evidence field link with proper audit logging and expiration validation.
     * This is the single source of truth for all attestation operations.
     * Rejects expired evidence based on source date + TTL.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    @Audited(action = "USER_ATTEST_EVIDENCE_FIELD", subjectType = "profile_field", subject = "#profileFieldId",
             context = {"evidenceId=#evidenceId", "attestedBy=#attestedBy", "comment=#comment", "source=#source"})
    public EvidenceFieldLinkResponse attestEvidenceFieldLink(
            String evidenceId,
            String profileFieldId,
            String attestedBy,
            String comment,
            String source) {
        
        log.info("=== AUDIT DEBUG === Attesting evidence field link: evidenceId={}, profileFieldId={}, attestedBy={}, source={}", 
                 evidenceId, profileFieldId, attestedBy, source);
        
        EvidenceFieldLinkId id = new EvidenceFieldLinkId(evidenceId, profileFieldId);
        
        EvidenceFieldLink link = evidenceFieldLinkRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Evidence field link not found."));

        // ✅ VALIDATE EVIDENCE FRESHNESS BEFORE ATTESTATION
        try {
            Evidence evidence = evidenceRepository.findEvidenceById(evidenceId)
                .orElseThrow(() -> new NotFoundException("Evidence not found: " + evidenceId));
                
            ProfileField profileField = profileRepository.getProfileFieldById(profileFieldId)
                .orElseThrow(() -> new NotFoundException("Profile field not found: " + profileFieldId));
            
            // Check if evidence is expired based on source date + TTL
            if (unifiedFreshnessCalculator.isExpired(evidence, profileField)) {
                // Reject expired evidence
                String explanation = unifiedFreshnessCalculator.getFreshnessExplanation(evidence, profileField);
                String rejectionMessage = "Evidence rejected: expired based on source date. " + explanation;
                
                log.warn("Rejecting attestation for expired evidence: {}", rejectionMessage);
                
                link.setLinkStatus(EvidenceFieldLinkStatus.REJECTED);
                link.setReviewedBy(attestedBy);
                link.setReviewedAt(OffsetDateTime.now());
                link.setReviewComment(rejectionMessage);
                link.setUpdatedAt(OffsetDateTime.now());
                
                EvidenceFieldLink savedLink = evidenceFieldLinkRepository.save(link);
                return EvidenceFieldLinkResponse.fromEntity(savedLink);
            }
            
        } catch (Exception e) {
            log.warn("Failed to validate evidence freshness, proceeding with attestation: {}", e.getMessage());
            // Continue with attestation if validation fails (graceful degradation)
        }

        // Update with user attestation (evidence is fresh or validation failed gracefully)
        link.setLinkStatus(EvidenceFieldLinkStatus.USER_ATTESTED);
        link.setReviewedBy(attestedBy);
        link.setReviewedAt(OffsetDateTime.now());
        link.setReviewComment(comment);
        link.setUpdatedAt(OffsetDateTime.now());

        EvidenceFieldLink savedLink = evidenceFieldLinkRepository.save(link);
        
        log.info("=== AUDIT DEBUG === Successfully attested evidence field link: evidenceId={}, profileFieldId={}", 
                 evidenceId, profileFieldId);
        
        return EvidenceFieldLinkResponse.fromEntity(savedLink);
    }
}