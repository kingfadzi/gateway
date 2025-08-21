package com.example.onboarding.controller.application;

import com.example.onboarding.dto.application.CreateAppRequest;
import com.example.onboarding.dto.application.ProfileSnapshot;
import com.example.onboarding.service.profile.AutoProfileService;
import com.example.onboarding.config.AutoProfileProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/apps")
public class ApplicationController {

    private final AutoProfileService autoProfileService;
    private final AutoProfileProperties props;

    public ApplicationController(AutoProfileService autoProfileService, AutoProfileProperties props) {
        this.autoProfileService = autoProfileService;
        this.props = props;
    }

    /** Minimal create: caller provides only appId; server fetches CIA+S+R and builds profile */
    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateAppRequest req,
                                    @RequestParam(name="autoProfile", defaultValue = "true") boolean autoProfile) {
        if (req == null || req.appId() == null || req.appId().isBlank()) {
            return ResponseEntity.badRequest().body("appId is required");
        }
        if (!autoProfile || !props.isEnabled()) {
            // If you want a minimal create without profile, wire a separate method.
            return ResponseEntity.badRequest().body("autoProfile is disabled; enable autoprofile.enabled=true or pass autoProfile=true");
        }
        ProfileSnapshot snap = autoProfileService.autoSetup(req.appId().trim());
        return ResponseEntity.ok(snap);
    }

    /** Rebuild endpoint (idempotent) */
    @PostMapping("/{appId}/auto-profile:rebuild")
    public ResponseEntity<?> rebuild(@PathVariable String appId) {
        ProfileSnapshot snap = autoProfileService.autoSetup(appId);
        return ResponseEntity.ok(snap);
    }
}
