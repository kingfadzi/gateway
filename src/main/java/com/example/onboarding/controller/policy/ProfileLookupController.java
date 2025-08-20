package com.example.onboarding.controller.policy;

import com.example.onboarding.service.policy.ProfileLookupService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internal")
public class ProfileLookupController {

    private final ProfileLookupService service;

    public ProfileLookupController(ProfileLookupService service) {
        this.service = service;
    }

    /** List field keys for the app’s latest profile. */
    @GetMapping("/apps/{appId}/field-keys")
    public List<String> listFieldKeys(@PathVariable String appId) {
        return service.listFieldKeys(appId);
    }

    /** Resolve fieldKey -> profile_field.id for the app’s latest profile. */
    @GetMapping("/apps/{appId}/field-id")
    public Map<String,String> resolveFieldId(@PathVariable String appId, @RequestParam String key) {
        var idOpt = service.resolveFieldId(appId, key);
        if (idOpt.isEmpty()) {
            throw new FieldNotFoundException("fieldKey '%s' not found for app %s".formatted(key, appId));
        }
        return Map.of("fieldKey", key, "profileFieldId", idOpt.get());
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    private static class FieldNotFoundException extends RuntimeException {
        FieldNotFoundException(String msg) { super(msg); }
    }
}
