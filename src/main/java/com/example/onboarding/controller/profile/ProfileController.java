package com.example.onboarding.controller.profile;

import com.example.onboarding.dto.PageResponse;
import com.example.onboarding.dto.profile.DomainGraphPayload;
import com.example.onboarding.dto.profile.PatchProfileRequest;
import com.example.onboarding.dto.profile.PatchProfileResponse;
import com.example.onboarding.dto.profile.ProfileFieldContext;
import com.example.onboarding.dto.profile.ProfilePayload;
import com.example.onboarding.dto.profile.SuggestedEvidence;
import com.example.onboarding.service.profile.ProfileService;
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
