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
        log.info("Creating track for app {}", appId);
        Track track = trackService.createTrack(appId, request);
        log.info("Successfully created track {} for app {}", track.trackId(), appId);
        return ResponseEntity.status(201).body(track);
    }
    
    /**
     * Create or return existing track (upsert behavior)
     * PUT /api/apps/{appId}/tracks
     */
    @PutMapping("/apps/{appId}/tracks")
    public ResponseEntity<Track> upsertTrack(@PathVariable String appId,
                                           @RequestBody CreateTrackRequest request) {
        log.info("Upserting track for app {}", appId);

        // First try to find existing track
        if (request.provider() != null && request.resourceType() != null && request.resourceId() != null) {
            var existingTrack = trackService.getTrackByResource(
                request.provider(), request.resourceType(), request.resourceId()
            );
            if (existingTrack.isPresent()) {
                log.info("Found existing track {} for upsert request", existingTrack.get().trackId());
                return ResponseEntity.ok(existingTrack.get());
            }
        }

        // No existing track found, create new one
        Track track = trackService.createTrack(appId, request);
        log.info("Successfully created new track {} for upsert", track.trackId());
        return ResponseEntity.status(201).body(track);
    }
    
    /**
     * Update an existing track
     * PUT /api/tracks/{trackId}
     */
    @PutMapping("/tracks/{trackId}")
    public ResponseEntity<Track> updateTrack(@PathVariable String trackId,
                                           @RequestBody UpdateTrackRequest request) {
        Track track = trackService.updateTrack(trackId, request);
        return ResponseEntity.ok(track);
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
        PageResponse<TrackSummary> tracks = trackService.getTracksByApp(appId, page, pageSize);
        return ResponseEntity.ok(tracks);
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
        return trackService.getTrackByResource(provider, resourceType, resourceId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}