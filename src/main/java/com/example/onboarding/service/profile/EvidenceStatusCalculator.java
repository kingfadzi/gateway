package com.example.onboarding.service.profile;

import com.example.onboarding.dto.evidence.EnhancedEvidenceSummary;
import com.example.onboarding.dto.profile.ProfileField;
import com.example.onboarding.repository.profile.ProfileRepository;
import com.example.onboarding.repository.document.DocumentRepository;
import com.example.onboarding.util.TtlParser;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Calculates approval and freshness status for profile fields based on their evidence
 */
@Component
public class EvidenceStatusCalculator {
    
    private final ProfileRepository profileRepository;
    private final DocumentRepository documentRepository;
    
    public EvidenceStatusCalculator(ProfileRepository profileRepository, DocumentRepository documentRepository) {
        this.profileRepository = profileRepository;
        this.documentRepository = documentRepository;
    }
    
    /**
     * Calculate approval status based on evidence link statuses
     */
    public String calculateApprovalStatus(List<EnhancedEvidenceSummary> evidence) {
        if (evidence == null || evidence.isEmpty()) {
            return "missing";
        }
        
        boolean hasApproved = false;
        boolean hasUserAttested = false;
        boolean hasRejected = false;
        boolean hasPending = false;
        
        for (EnhancedEvidenceSummary ev : evidence) {
            switch (ev.linkStatus()) {
                case APPROVED -> hasApproved = true;
                case USER_ATTESTED -> hasUserAttested = true;
                case REJECTED -> hasRejected = true;
                case PENDING_REVIEW, PENDING_PO_REVIEW, PENDING_SME_REVIEW, ATTACHED -> hasPending = true;
            }
        }
        
        // Apply approval status logic - distinguish between approved and user_attested
        if ((hasApproved || hasUserAttested) && !hasRejected && !hasPending) {
            // If we have both types, prioritize showing the formal approval
            if (hasApproved) {
                return "approved";      // Formally approved by reviewer
            } else {
                return "user_attested"; // Self-attested by user
            }
        } else if (hasRejected && !hasApproved && !hasUserAttested && !hasPending) {
            return "rejected";  // ALL evidence is rejected
        } else {
            return "pending";   // ANY OTHER COMBINATION
        }
    }
    
    /**
     * Calculate freshness status based on document source dates and TTL
     */
    public String calculateFreshnessStatus(List<EnhancedEvidenceSummary> evidence, String profileFieldId) {
        if (evidence == null || evidence.isEmpty()) {
            return "current"; // No evidence = no freshness concerns
        }
        
        // Get profile field for TTL calculation
        Optional<ProfileField> profileFieldOpt = profileRepository.getProfileFieldById(profileFieldId);
        if (profileFieldOpt.isEmpty()) {
            return "current"; // Can't calculate without field info
        }
        
        Duration ttl = TtlParser.extractTtl(profileFieldOpt.get().value());
        OffsetDateTime now = OffsetDateTime.now();
        
        boolean hasCurrentEvidence = false;
        boolean hasExpiringEvidence = false;
        boolean hasExpiredEvidence = false;
        boolean hasBrokenEvidence = false;
        
        for (EnhancedEvidenceSummary ev : evidence) {
            // Only consider approved/attested and active evidence for freshness
            if ((ev.linkStatus() != com.example.onboarding.model.EvidenceFieldLinkStatus.APPROVED && 
                 ev.linkStatus() != com.example.onboarding.model.EvidenceFieldLinkStatus.USER_ATTESTED) || 
                !"active".equals(ev.status())) {
                continue;
            }
            
            // Check document link health
            if (ev.documentLinkHealth() == null || ev.documentLinkHealth() != 200) {
                hasBrokenEvidence = true;
                continue;
            }
            
            // Calculate expiration based on evidence creation date + TTL
            // (We'll use evidence.createdAt() as proxy for document source date for now)
            OffsetDateTime evidenceDate = ev.createdAt();
            OffsetDateTime expiration = evidenceDate.plus(ttl);
            
            if (expiration.isBefore(now)) {
                hasExpiredEvidence = true;
            } else if (expiration.isBefore(now.plusDays(30))) {
                hasExpiringEvidence = true;
            } else {
                hasCurrentEvidence = true;
            }
        }
        
        // Priority order for freshness status
        if (hasCurrentEvidence) {
            return "current";
        } else if (hasExpiringEvidence) {
            return "expiring";
        } else if (hasExpiredEvidence) {
            return "expired";
        } else if (hasBrokenEvidence) {
            return "broken";
        } else {
            return "current"; // Default fallback
        }
    }
}