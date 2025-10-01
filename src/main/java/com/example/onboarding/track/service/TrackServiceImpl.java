package com.example.onboarding.track.service;

import com.example.onboarding.application.dto.PageResponse;
import com.example.onboarding.track.dto.CreateTrackRequest;
import com.example.onboarding.track.dto.Track;
import com.example.onboarding.track.dto.TrackSummary;
import com.example.onboarding.track.dto.UpdateTrackRequest;
import com.example.onboarding.track.repository.TrackRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class TrackServiceImpl implements TrackService {
    
    private static final Logger log = LoggerFactory.getLogger(TrackServiceImpl.class);
    
    private final TrackRepository trackRepository;
    
    // Valid enum values for validation
    private static final Set<String> VALID_INTENTS = Set.of("compliance", "risk", "security", "architecture");
    private static final Set<String> VALID_STATUSES = Set.of("open", "in_review", "closed");
    private static final Set<String> VALID_RESULTS = Set.of("pass", "fail", "waived", "abandoned");
    private static final Set<String> VALID_PROVIDERS = Set.of("jira", "snow", "gitlab", "manual", "policy");
    private static final Set<String> VALID_RESOURCE_TYPES = Set.of("epic", "change", "mr", "note", "control");
    
    public TrackServiceImpl(TrackRepository trackRepository) {
        this.trackRepository = trackRepository;
    }
    
    @Override
    @Transactional
    public Track createTrack(String appId, CreateTrackRequest request) {
        log.debug("Creating track for app {}: {}", appId, request);
        
        // Validate inputs
        try {
            validateCreateRequest(request);
            log.debug("Request validation passed for app {}", appId);
        } catch (IllegalArgumentException e) {
            log.error("Request validation failed for app {}: {}", appId, e.getMessage());
            throw e;
        }
        
        // Check if application exists
        boolean appExists = trackRepository.applicationExists(appId);
        log.debug("Application {} exists: {}", appId, appExists);
        if (!appExists) {
            throw new IllegalArgumentException("Application not found: " + appId);
        }
        
        // Check for duplicate resource reference
        if (request.provider() != null && request.resourceType() != null && request.resourceId() != null) {
            log.debug("Checking for duplicate track: {}:{}:{}", request.provider(), request.resourceType(), request.resourceId());
            Optional<Track> existing = trackRepository.findTrackByResource(request.provider(), request.resourceType(), request.resourceId());
            if (existing.isPresent()) {
                log.error("Duplicate track found for {}:{}:{}", request.provider(), request.resourceType(), request.resourceId());
                throw new IllegalArgumentException(
                    String.format("Track already exists for %s:%s:%s", request.provider(), request.resourceType(), request.resourceId())
                );
            }
            log.debug("No duplicate track found");
        }
        
        // Set default opened time if not provided
        OffsetDateTime openedAt = request.openedAt() != null ? request.openedAt() : OffsetDateTime.now();
        
        // Create the track
        String trackId = trackRepository.createTrack(
            appId,
            request.title(),
            request.intent(),
            request.provider(),
            request.resourceType(),
            request.resourceId(),
            request.uri(),
            request.attributes(),
            openedAt
        );
        
        log.info("Created track {} for app {} ({}:{}:{})", trackId, appId, request.provider(), request.resourceType(), request.resourceId());
        
        // Return the created track
        return trackRepository.findTrackById(trackId)
            .orElseThrow(() -> new RuntimeException("Failed to retrieve created track"));
    }
    
    @Override
    @Transactional
    public Track updateTrack(String trackId, UpdateTrackRequest request) {
        // Validate inputs
        validateUpdateRequest(request);
        
        // Check if track exists
        Optional<Track> existingTrack = trackRepository.findTrackById(trackId);
        if (existingTrack.isEmpty()) {
            throw new IllegalArgumentException("Track not found: " + trackId);
        }
        
        // Auto-set closed_at when status changes to 'closed'
        OffsetDateTime closedAt = request.closedAt();
        if ("closed".equals(request.status()) && closedAt == null) {
            closedAt = OffsetDateTime.now();
        }
        
        // Update the track
        boolean updated = trackRepository.updateTrack(
            trackId,
            request.title(),
            request.status(),
            request.result(),
            closedAt
        );
        
        if (!updated) {
            throw new RuntimeException("Failed to update track: " + trackId);
        }
        
        log.info("Updated track {} (status={}, result={})", trackId, request.status(), request.result());
        
        // Return the updated track
        return trackRepository.findTrackById(trackId)
            .orElseThrow(() -> new RuntimeException("Failed to retrieve updated track"));
    }
    
    @Override
    public Optional<Track> getTrackById(String trackId) {
        return trackRepository.findTrackById(trackId);
    }
    
    @Override
    public PageResponse<TrackSummary> getTracksByApp(String appId, int page, int pageSize) {
        // Validate pagination parameters
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        int offset = (safePage - 1) * safePageSize;
        
        // Check if application exists
        if (!trackRepository.applicationExists(appId)) {
            throw new IllegalArgumentException("Application not found: " + appId);
        }
        
        // Get total count and paginated results
        long total = trackRepository.countTracksByApp(appId);
        List<TrackSummary> tracks = trackRepository.findTracksByApp(appId, safePageSize, offset);
        
        return new PageResponse<>(safePage, safePageSize, total, tracks);
    }
    
    @Override
    public Optional<Track> getTrackByResource(String provider, String resourceType, String resourceId) {
        if (provider == null || resourceType == null || resourceId == null) {
            throw new IllegalArgumentException("Provider, resource type, and resource ID are required");
        }
        return trackRepository.findTrackByResource(provider, resourceType, resourceId);
    }
    
    private void validateCreateRequest(CreateTrackRequest request) {
        log.debug("Validating request: {}", request);
        
        if (request.title() == null || request.title().trim().isEmpty()) {
            log.error("Title validation failed: title is null or empty");
            throw new IllegalArgumentException("Title is required");
        }
        
        if (request.intent() != null && !VALID_INTENTS.contains(request.intent())) {
            log.error("Intent validation failed: '{}' not in {}", request.intent(), VALID_INTENTS);
            throw new IllegalArgumentException("Invalid intent. Valid values: " + VALID_INTENTS);
        }
        
        if (request.provider() != null && !VALID_PROVIDERS.contains(request.provider())) {
            log.error("Provider validation failed: '{}' not in {}", request.provider(), VALID_PROVIDERS);
            throw new IllegalArgumentException("Invalid provider. Valid values: " + VALID_PROVIDERS);
        }
        
        if (request.resourceType() != null && !VALID_RESOURCE_TYPES.contains(request.resourceType())) {
            log.error("ResourceType validation failed: '{}' not in {}", request.resourceType(), VALID_RESOURCE_TYPES);
            throw new IllegalArgumentException("Invalid resource type. Valid values: " + VALID_RESOURCE_TYPES);
        }
        
        log.debug("Request validation completed successfully");
    }
    
    private void validateUpdateRequest(UpdateTrackRequest request) {
        if (request.status() != null && !VALID_STATUSES.contains(request.status())) {
            throw new IllegalArgumentException("Invalid status. Valid values: " + VALID_STATUSES);
        }
        
        if (request.result() != null && !VALID_RESULTS.contains(request.result())) {
            throw new IllegalArgumentException("Invalid result. Valid values: " + VALID_RESULTS);
        }
    }
}