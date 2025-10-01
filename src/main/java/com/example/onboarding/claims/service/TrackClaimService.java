package com.example.onboarding.claims.service;

import com.example.onboarding.application.dto.PageResponse;
import com.example.onboarding.claims.dto.ClaimSummary;
import com.example.onboarding.claims.dto.ClaimWithEvidence;
import com.example.onboarding.claims.dto.EnsureClaimRequest;
import com.example.onboarding.policy.repository.ControlClaimRepository;
import com.example.onboarding.track.repository.TrackRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class TrackClaimService {
    
    private static final Logger log = LoggerFactory.getLogger(TrackClaimService.class);
    
    private final ControlClaimRepository claimRepository;
    private final TrackRepository trackRepository;
    
    public TrackClaimService(ControlClaimRepository claimRepository, TrackRepository trackRepository) {
        this.claimRepository = claimRepository;
        this.trackRepository = trackRepository;
    }
    
    /**
     * Ensure a claim exists for the given app, field, and track
     * Creates new claim if not found, returns existing if found
     */
    @Transactional
    public ClaimSummary ensureClaim(String appId, String trackId, EnsureClaimRequest request) {
        log.debug("Ensuring claim for app {} track {} request: {}", appId, trackId, request);
        
        // Validate request
        if (request.fieldKey() == null || request.fieldKey().trim().isEmpty()) {
            log.error("Field key validation failed: fieldKey is null or empty");
            throw new IllegalArgumentException("Field key is required");
        }
        
        // Validate track exists and belongs to the app
        log.debug("Validating track {} exists for app {}", trackId, appId);
        var trackOpt = trackRepository.findTrackById(trackId);
        if (trackOpt.isEmpty()) {
            log.error("Track validation failed: track {} not found", trackId);
            throw new IllegalArgumentException("Track not found: " + trackId);
        }
        
        var track = trackOpt.get();
        if (!track.appId().equals(appId)) {
            log.error("Track validation failed: track {} belongs to app {} but expected {}", trackId, track.appId(), appId);
            throw new IllegalArgumentException("Track " + trackId + " does not belong to app " + appId);
        }
        
        log.debug("Track validation passed: track {} belongs to app {}", trackId, appId);
        
        String method = request.method() != null ? request.method() : "manual";
        log.debug("Using method: {}", method);
        
        try {
            ClaimSummary claim = claimRepository.ensureClaim(appId, request.fieldKey(), trackId, method);
            log.info("Successfully ensured claim {} for app {} field {} track {}", 
                claim.claimId(), appId, request.fieldKey(), trackId);
            return claim;
        } catch (Exception e) {
            log.error("Failed to ensure claim in repository for app {} track {} field {}: {}", 
                appId, trackId, request.fieldKey(), e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Get claim with all attached evidence
     */
    public Optional<ClaimWithEvidence> getClaimWithEvidence(String claimId) {
        return claimRepository.findClaimWithEvidence(claimId);
    }
    
    /**
     * List claims for a track with pagination
     */
    public PageResponse<ClaimSummary> getClaimsByTrack(String trackId, int page, int pageSize) {
        // Validate pagination parameters
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        int offset = (safePage - 1) * safePageSize;
        
        // Get total count and paginated results
        long total = claimRepository.countClaimsByTrack(trackId);
        List<ClaimSummary> claims = claimRepository.findClaimsByTrack(trackId, safePageSize, offset);
        
        return new PageResponse<>(safePage, safePageSize, total, claims);
    }
}