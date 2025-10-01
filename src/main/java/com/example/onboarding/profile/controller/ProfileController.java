package com.example.onboarding.profile.controller;

import com.example.onboarding.profile.dto.DomainGraphPayload;
import com.example.onboarding.profile.dto.PatchProfileRequest;
import com.example.onboarding.profile.dto.PatchProfileResponse;
import com.example.onboarding.profile.dto.ProfileFieldContext;
import com.example.onboarding.profile.dto.SuggestedEvidence;
import com.example.onboarding.profile.service.ProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/apps/{appId}")
public class ProfileController {

    private final ProfileService svc;

    public ProfileController(ProfileService svc) {
        this.svc = svc;
    }

    @GetMapping("/profile")
    public DomainGraphPayload getProfile(@PathVariable String appId) {
        return svc.getProfileDomainGraph(appId);
    }

    @GetMapping("/profile/field/{fieldKey}")
    public ResponseEntity<ProfileFieldContext> getFieldContext(@PathVariable String appId, 
                                                               @PathVariable String fieldKey) {
        ProfileFieldContext context = svc.getProfileFieldContext(appId, fieldKey);
        if (context == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(context);
    }

    @GetMapping("/profile/field/{fieldKey}/suggested-evidence")
    public ResponseEntity<SuggestedEvidence> getSuggestedEvidence(@PathVariable String appId,
                                                                  @PathVariable String fieldKey) {
        SuggestedEvidence suggestions = svc.getSuggestedEvidence(appId, fieldKey);
        if (suggestions == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(suggestions);
    }

    @PatchMapping("/profile")
    public PatchProfileResponse patchProfile(@PathVariable String appId, @RequestBody PatchProfileRequest req) {
        return svc.patchProfile(appId, req);
    }

}
