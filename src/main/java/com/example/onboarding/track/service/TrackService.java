package com.example.onboarding.track.service;

import com.example.onboarding.application.dto.PageResponse;
import com.example.onboarding.track.dto.CreateTrackRequest;
import com.example.onboarding.track.dto.Track;
import com.example.onboarding.track.dto.TrackSummary;
import com.example.onboarding.track.dto.UpdateTrackRequest;

import java.util.Optional;

public interface TrackService {
    
    /**
     * Create a new track for an application
     */
    Track createTrack(String appId, CreateTrackRequest request);
    
    /**
     * Update an existing track
     */
    Track updateTrack(String trackId, UpdateTrackRequest request);
    
    /**
     * Get track by ID
     */
    Optional<Track> getTrackById(String trackId);
    
    /**
     * Get tracks for an application with pagination
     */
    PageResponse<TrackSummary> getTracksByApp(String appId, int page, int pageSize);
    
    /**
     * Find track by provider, resource type, and resource ID
     */
    Optional<Track> getTrackByResource(String provider, String resourceType, String resourceId);
}