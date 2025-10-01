package com.example.gateway.track.controller;

import com.example.gateway.application.dto.PageResponse;
import com.example.gateway.track.dto.CreateTrackRequest;
import com.example.gateway.track.dto.Track;
import com.example.gateway.track.dto.TrackSummary;
import com.example.gateway.track.dto.UpdateTrackRequest;
import com.example.gateway.track.service.TrackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class TrackController {
    
    private static final Logger log = LoggerFactory.getLogger(TrackController.class);
    private final TrackService trackService;
    
    public TrackController(TrackService trackService) {
        this.trackService = trackService;
    }
    
    /**
     * Create a new track for an application
     * POST /api/apps/{appId}/tracks
     */
    @PostMapping("/apps/{appId}/tracks")
    public ResponseEntity<Track> createTrack(@PathVariable String appId,
                                           @RequestBody CreateTrackRequest request) {
        log.info("Creating track for app {} with request: {}", appId, request);
        try {
            Track track = trackService.createTrack(appId, request);
            log.info("Successfully created track {} for app {}", track.trackId(), appId);
            return ResponseEntity.status(201).body(track);
        } catch (IllegalArgumentException e) {
            String errorMessage = e.getMessage();
            
            // Handle duplicate track case specifically
            if (errorMessage != null && errorMessage.contains("Track already exists")) {
                log.info("Track already exists for app {}, returning existing track instead of failing", appId);
                
                // Try to find and return the existing track
                if (request.provider() != null && request.resourceType() != null && request.resourceId() != null) {
                    try {
                        var existingTrack = trackService.getTrackByResource(
                            request.provider(), request.resourceType(), request.resourceId()
                        );
                        if (existingTrack.isPresent()) {
                            log.info("Successfully returned existing track {} for duplicate create request", existingTrack.get().trackId());
                            return ResponseEntity.ok(existingTrack.get()); // 200 OK with existing track
                        }
                    } catch (Exception ex) {
                        log.warn("Could not retrieve existing track: {}", ex.getMessage());
                    }
                }
                
                log.error("Track exists but could not retrieve it for app {}", appId);
                return ResponseEntity.internalServerError().build();
            }
            
            // Handle other validation errors
            log.error("Validation error creating track for app {}: {}", appId, errorMessage, e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Unexpected error creating track for app {}: {}", appId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Create or return existing track (upsert behavior)
     * PUT /api/apps/{appId}/tracks
     */
    @PutMapping("/apps/{appId}/tracks")
    public ResponseEntity<Track> upsertTrack(@PathVariable String appId,
                                           @RequestBody CreateTrackRequest request) {
        log.info("Upserting track for app {} with request: {}", appId, request);
        
        // First try to find existing track
        if (request.provider() != null && request.resourceType() != null && request.resourceId() != null) {
            try {
                var existingTrack = trackService.getTrackByResource(
                    request.provider(), request.resourceType(), request.resourceId()
                );
                if (existingTrack.isPresent()) {
                    log.info("Found existing track {} for upsert request", existingTrack.get().trackId());
                    return ResponseEntity.ok(existingTrack.get());
                }
            } catch (Exception e) {
                log.warn("Error checking for existing track during upsert: {}", e.getMessage());
            }
        }
        
        // No existing track found, create new one
        try {
            Track track = trackService.createTrack(appId, request);
            log.info("Successfully created new track {} for upsert", track.trackId());
            return ResponseEntity.status(201).body(track);
        } catch (IllegalArgumentException e) {
            log.error("Validation error during upsert for app {}: {}", appId, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Unexpected error during upsert for app {}: {}", appId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Update an existing track
     * PUT /api/tracks/{trackId}
     */
    @PutMapping("/tracks/{trackId}")
    public ResponseEntity<Track> updateTrack(@PathVariable String trackId,
                                           @RequestBody UpdateTrackRequest request) {
        try {
            Track track = trackService.updateTrack(trackId, request);
            return ResponseEntity.ok(track);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Get track by ID
     * GET /api/tracks/{trackId}
     */
    @GetMapping("/tracks/{trackId}")
    public ResponseEntity<Track> getTrack(@PathVariable String trackId) {
        return trackService.getTrackById(trackId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Get tracks for an application with pagination
     * GET /api/apps/{appId}/tracks?page=1&pageSize=10
     */
    @GetMapping("/apps/{appId}/tracks")
    public ResponseEntity<PageResponse<TrackSummary>> getTracks(
            @PathVariable String appId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        try {
            PageResponse<TrackSummary> tracks = trackService.getTracksByApp(appId, page, pageSize);
            return ResponseEntity.ok(tracks);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Get track by provider, resource type, and resource ID
     * GET /api/tracks/by-resource?provider=jira&resourceType=epic&resourceId=PROJ-123
     */
    @GetMapping("/tracks/by-resource")
    public ResponseEntity<Track> getTrackByResource(
            @RequestParam String provider,
            @RequestParam String resourceType,
            @RequestParam String resourceId) {
        try {
            return trackService.getTrackByResource(provider, resourceType, resourceId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}