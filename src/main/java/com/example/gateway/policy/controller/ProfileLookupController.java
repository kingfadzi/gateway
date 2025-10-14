package com.example.gateway.policy.controller;

import com.example.gateway.policy.service.ProfileLookupService;
import com.example.gateway.profile.service.ProfileFieldRegistryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internal")
@Tag(name = "Profile & Registry", description = "Profile field lookup and registry management API")
public class ProfileLookupController {

    private final ProfileLookupService service;
    private final ProfileFieldRegistryService registryService;

    public ProfileLookupController(ProfileLookupService service, ProfileFieldRegistryService registryService) {
        this.service = service;
        this.registryService = registryService;
    }

    @GetMapping("/apps/{appId}/field-keys")
    @Operation(summary = "List field keys for app", description = "Retrieve all field keys from the app's latest profile")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Field keys retrieved successfully")
    })
    public List<String> listFieldKeys(
            @Parameter(description = "Application ID") @PathVariable String appId) {
        return service.listFieldKeys(appId);
    }

    @GetMapping("/apps/{appId}/field-id")
    @Operation(summary = "Resolve field ID", description = "Resolve field key to profile_field.id for the app's latest profile")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Field ID resolved successfully"),
        @ApiResponse(responseCode = "404", description = "Field key not found for app")
    })
    public Map<String,String> resolveFieldId(
            @Parameter(description = "Application ID") @PathVariable String appId,
            @Parameter(description = "Field key to resolve") @RequestParam String key) {
        var idOpt = service.resolveFieldId(appId, key);
        if (idOpt.isEmpty()) {
            throw new FieldNotFoundException("fieldKey '%s' not found for app %s".formatted(key, appId));
        }
        return Map.of("fieldKey", key, "profileFieldId", idOpt.get());
    }

    @GetMapping("/profile-fields-registry")
    @Operation(summary = "Get profile field registry", description = "Retrieve the complete raw registry data from profile-fields.registry.yaml")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Registry data retrieved successfully")
    })
    public Map<String, Object> getProfileFieldRegistry() {
        return registryService.getRawRegistry();
    }

    @GetMapping("/profile-fields-registry/domains")
    @Operation(summary = "List all domains", description = "List all unique domains (derived_from values) in the registry")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Domains retrieved successfully")
    })
    public List<String> listDomains() {
        return registryService.getDomains();
    }

    @GetMapping("/profile-fields-registry/domains/{domain}/controls")
    @Operation(summary = "List controls by domain", description = "List all control keys for a specific domain")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Controls retrieved successfully")
    })
    public List<String> listControlsByDomain(
            @Parameter(description = "Domain name (e.g., security_rating, integrity_rating)") @PathVariable String domain) {
        return registryService.getControlsByDomain(domain);
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    private static class FieldNotFoundException extends RuntimeException {
        FieldNotFoundException(String msg) { super(msg); }
    }
}
