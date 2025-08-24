package com.example.onboarding.controller.profile;

import com.example.onboarding.dto.PageResponse;
import com.example.onboarding.dto.evidence.CreateEvidenceRequest;
import com.example.onboarding.dto.evidence.Evidence;
import com.example.onboarding.dto.profile.DomainGraphPayload;
import com.example.onboarding.dto.profile.PatchProfileRequest;
import com.example.onboarding.dto.profile.PatchProfileResponse;
import com.example.onboarding.dto.profile.ProfilePayload;
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

    @PatchMapping("/profile")
    public PatchProfileResponse patchProfile(@PathVariable String appId, @RequestBody PatchProfileRequest req) {
        return svc.patchProfile(appId, req);
    }

    @GetMapping("/evidence")
    public PageResponse<Evidence> listEvidence(@PathVariable String appId,
                                               @RequestParam(required = false, name = "key") String fieldKey, // keeps ?key= for UI
                                               @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(name="page_size", defaultValue = "25") int pageSize) {
        return svc.listEvidence(appId, fieldKey, page, pageSize);
    }

    @PostMapping("/evidence")
    public ResponseEntity<Evidence> addEvidence(@PathVariable String appId,
                                                @RequestBody CreateEvidenceRequest req) {
        Evidence created = svc.addEvidence(appId, req);
        return ResponseEntity.status(201).body(created);
    }

    @DeleteMapping("/evidence/{evidenceId}")
    public ResponseEntity<Void> deleteEvidence(@PathVariable String appId,
                                               @PathVariable String evidenceId) {
        svc.deleteEvidence(appId, evidenceId);
        return ResponseEntity.noContent().build();
    }
}
