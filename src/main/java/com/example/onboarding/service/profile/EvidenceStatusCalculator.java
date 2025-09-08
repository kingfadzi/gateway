package com.example.onboarding.service.profile;

import com.example.onboarding.dto.evidence.EnhancedEvidenceSummary;
import com.example.onboarding.dto.profile.ProfileField;
import com.example.onboarding.repository.profile.ProfileRepository;
import com.example.onboarding.repository.document.DocumentRepository;
import com.example.onboarding.service.evidence.UnifiedFreshnessCalculator;
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
    private final UnifiedFreshnessCalculator unifiedFreshnessCalculator;
    
    public EvidenceStatusCalculator(ProfileRepository profileRepository, DocumentRepository documentRepository,
                                    UnifiedFreshnessCalculator unifiedFreshnessCalculator) {
        this.profileRepository = profileRepository;
        this.documentRepository = documentRepository;
        this.unifiedFreshnessCalculator = unifiedFreshnessCalculator;
    }
    
    /**
     * Calculate approval status based on evidence link statuses.
     * 
     * New 6-status system:
     * - approved: all attached evidence is approved (no pending/rejected/attested)
     * - partially_approved: mix of approved + user-attested (needs SME review to complete)  
     * - pending_review: at least one item is awaiting review (and none rejected)
     * - rejected: at least one item rejected
     * - user_attested: only self-attested evidence present
     * - no_evidence: nothing attached
     */
    public String calculateApprovalStatus(List<EnhancedEvidenceSummary> evidence) {
        if (evidence == null || evidence.isEmpty()) {
            return "no_evidence";
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
        
        // Apply new 6-status approval logic
        if (hasRejected) {
            return "rejected";  // At least one item rejected
        } else if (hasPending) {
            return "pending_review";  // At least one item awaiting review (and none rejected)
        } else if (hasApproved && hasUserAttested) {
            return "partially_approved";  // Mix of approved + user-attested (needs SME review)
        } else if (hasApproved && !hasUserAttested) {
            return "approved";  // All attached evidence is approved (no pending/rejected/attested)
        } else if (!hasApproved && hasUserAttested) {
            return "user_attested";  // Only self-attested evidence present
        } else {
            // Should not happen with non-empty evidence list, but fallback
            return "no_evidence";
        }
    }
    
    /**
     * Calculate freshness status based on source dates and TTL using unified logic.
     * This replaces the old createdAt + TTL approach with source-date-based calculation.
     * 
     * Returns: "current", "expiring", "expired", "broken", "invalid_evidence", "no_evidence"
     */
    public String calculateFreshnessStatus(List<EnhancedEvidenceSummary> evidence, String profileFieldId) {
        if (evidence == null || evidence.isEmpty()) {
            return "no_evidence"; // No evidence attached
        }
        
        // Get profile field for TTL calculation
        Optional<ProfileField> profileFieldOpt = profileRepository.getProfileFieldById(profileFieldId);
        if (profileFieldOpt.isEmpty()) {
            return "invalid_evidence"; // Can't calculate without field info
        }
        
        ProfileField profileField = profileFieldOpt.get();
        
        boolean hasCurrentEvidence = false;
        boolean hasExpiringEvidence = false;
        boolean hasExpiredEvidence = false;
        boolean hasBrokenEvidence = false;
        boolean hasInvalidEvidence = false;
        boolean hasApprovedOrAttestedEvidence = false;
        
        for (EnhancedEvidenceSummary ev : evidence) {
            // Calculate freshness for ALL evidence types (approved, rejected, pending)
            
            // Check document link health
            if (ev.documentLinkHealth() == null || ev.documentLinkHealth() != 200) {
                hasBrokenEvidence = true;
                continue;
            }
            
            // Track if we have approved/attested evidence (for information only)
            if (ev.linkStatus() == com.example.onboarding.model.EvidenceFieldLinkStatus.APPROVED || 
                ev.linkStatus() == com.example.onboarding.model.EvidenceFieldLinkStatus.USER_ATTESTED) {
                hasApprovedOrAttestedEvidence = true;
            }
            
            // ✅ Calculate freshness for ALL evidence regardless of approval status
            String freshness = unifiedFreshnessCalculator.calculateFreshness(
                ev.docVersionId(),  // For document source date lookup
                ev.validFrom(),     // Fallback #1
                ev.createdAt(),     // Fallback #2
                profileField
            );
            
            // Track freshness categories from all evidence
            switch (freshness) {
                case "current" -> hasCurrentEvidence = true;
                case "expiring" -> hasExpiringEvidence = true;
                case "expired" -> hasExpiredEvidence = true;
                case "invalid_evidence" -> hasInvalidEvidence = true;
            }
        }
        
        // Priority order for freshness status
        if (hasInvalidEvidence && !hasCurrentEvidence && !hasExpiringEvidence && !hasExpiredEvidence && !hasBrokenEvidence) {
            return "invalid_evidence"; // All evidence has anomalies
        } else if (hasCurrentEvidence) {
            return "current"; // Best case wins
        } else if (hasExpiringEvidence) {
            return "expiring";
        } else if (hasExpiredEvidence) {
            return "expired";
        } else if (hasBrokenEvidence) {
            return "broken"; // All links broken
        } else {
            return "invalid_evidence"; // Fallback for unexpected state
        }
    }
}