package com.example.gateway.evidence.service;

import com.example.gateway.evidence.dto.Evidence;
import com.example.gateway.profile.dto.ProfileField;
import com.example.gateway.util.TtlParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Unified service for calculating evidence freshness based on source dates.
 * Single source of truth that replaces all existing freshness calculation logic.
 * 
 * Uses source-date-based expiration: sourceDate + TTL from profile field policy.
 */
@Service
public class UnifiedFreshnessCalculator {

    private static final Logger log = LoggerFactory.getLogger(UnifiedFreshnessCalculator.class);
    
    private final SourceDateResolver sourceResolver;

    public UnifiedFreshnessCalculator(SourceDateResolver sourceResolver) {
        this.sourceResolver = sourceResolver;
    }

    /**
     * Calculate freshness status for evidence.
     * 
     * @param docVersionId Document version ID for source date lookup
     * @param validFrom Evidence validFrom field
     * @param createdAt Evidence creation time (fallback)
     * @param profileField Profile field containing TTL policy
     * @return "current", "expiring", "expired", "invalid_evidence"
     */
    public String calculateFreshness(String docVersionId, OffsetDateTime validFrom, 
                                   OffsetDateTime createdAt, ProfileField profileField) {
        
        try {
            // Step 1: Resolve actual source date with fallbacks
            OffsetDateTime sourceDate = sourceResolver.resolveSourceDate(docVersionId, validFrom, createdAt);
            
            // Validate source date is not null
            if (sourceDate == null) {
                log.warn("No source date resolvable for evidence - docVersionId: {}, validFrom: {}, createdAt: {}", 
                         docVersionId, validFrom, createdAt);
                return "invalid_evidence";
            }
            
            // Check for future-dated evidence
            OffsetDateTime now = OffsetDateTime.now();
            if (sourceDate.isAfter(now)) {
                log.warn("Future-dated evidence detected - sourceDate: {}, now: {}", sourceDate, now);
                return "invalid_evidence";
            }
            
            // Step 2: Get TTL from profile field policy  
            Duration ttl = TtlParser.extractTtl(profileField.value());
            
            // Validate TTL
            if (ttl == null || ttl.isZero() || ttl.isNegative()) {
                log.warn("Invalid TTL from profile field - ttl: {}, fieldValue: {}", ttl, profileField.value());
                return "invalid_evidence";
            }
            
            // Step 3: Calculate expiration = source date + TTL
            OffsetDateTime expiration = sourceDate.plus(ttl);
            
            log.debug("Freshness calculation - sourceDate: {}, ttl: {}, expiration: {}, now: {}", 
                      sourceDate, ttl, expiration, now);
            
            // Step 4: Determine freshness category
            if (expiration.isBefore(now)) {
                return "expired";
            } else if (expiration.isBefore(now.plusDays(30))) {
                return "expiring";
            } else {
                return "current";
            }
            
        } catch (Exception e) {
            log.warn("Exception during freshness calculation - docVersionId: {}, error: {}", 
                     docVersionId, e.getMessage());
            return "invalid_evidence";
        }
    }

    /**
     * Calculate freshness status for an Evidence object.
     * Convenience method that extracts the necessary fields.
     */
    public String calculateFreshness(Evidence evidence, ProfileField profileField) {
        return calculateFreshness(
            evidence.docVersionId(),
            evidence.validFrom(), 
            evidence.createdAt(),
            profileField
        );
    }

    /**
     * Calculate validUntil date for evidence.
     * This is sourceDate + TTL and should be computed during evidence creation.
     */
    public OffsetDateTime calculateValidUntil(String docVersionId, OffsetDateTime validFrom, 
                                            OffsetDateTime createdAt, ProfileField profileField) {
        
        OffsetDateTime sourceDate = sourceResolver.resolveSourceDate(docVersionId, validFrom, createdAt);
        Duration ttl = TtlParser.extractTtl(profileField.value());
        
        OffsetDateTime validUntil = sourceDate.plus(ttl);
        log.debug("Calculated validUntil: {} (sourceDate: {} + ttl: {})", validUntil, sourceDate, ttl);
        
        return validUntil;
    }

    /**
     * Calculate validUntil for an Evidence object.
     * Convenience method for evidence creation/updates.
     */
    public OffsetDateTime calculateValidUntil(Evidence evidence, ProfileField profileField) {
        return calculateValidUntil(
            evidence.docVersionId(),
            evidence.validFrom(),
            evidence.createdAt(),
            profileField
        );
    }

    /**
     * Check if evidence is expired (for attestation validation).
     */
    public boolean isExpired(Evidence evidence, ProfileField profileField) {
        return "expired".equals(calculateFreshness(evidence, profileField));
    }

    /**
     * Get human-readable freshness explanation for logging/debugging.
     */
    public String getFreshnessExplanation(Evidence evidence, ProfileField profileField) {
        OffsetDateTime sourceDate = sourceResolver.resolveSourceDate(
            evidence.docVersionId(), evidence.validFrom(), evidence.createdAt());
        Duration ttl = TtlParser.extractTtl(profileField.value());
        OffsetDateTime expiration = sourceDate.plus(ttl);
        
        return String.format("Evidence source date: %s, TTL: %s, expires: %s", 
                           sourceDate, ttl, expiration);
    }
}