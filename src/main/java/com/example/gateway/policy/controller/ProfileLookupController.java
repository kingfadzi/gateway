package com.example.gateway.policy.controller;

import com.example.gateway.policy.service.ProfileLookupService;
import com.example.gateway.profile.service.ProfileFieldRegistryService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internal")
public class ProfileLookupController {

    private final ProfileLookupService service;
    private final ProfileFieldRegistryService registryService;

    public ProfileLookupController(ProfileLookupService service, ProfileFieldRegistryService registryService) {
        this.service = service;
        this.registryService = registryService;
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

    @GetMapping("/profile-fields-registry")
    public Map<String, Object> getProfileFieldRegistry() {
        return registryService.getRawRegistry();
    }

    @GetMapping("/profile-fields-registry/domains")
    public List<String> listDomains() {
        return registryService.getDomains();
    }

    @GetMapping("/profile-fields-registry/domains/{domain}/controls")
    public List<String> listControlsByDomain(@PathVariable String domain) {
        return registryService.getControlsByDomain(domain);
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    private static class FieldNotFoundException extends RuntimeException {
        FieldNotFoundException(String msg) { super(msg); }
    }
}
