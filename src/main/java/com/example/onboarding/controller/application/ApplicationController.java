package com.example.onboarding.controller.application;

import com.example.onboarding.dto.application.CreateAppRequest;
import com.example.onboarding.dto.application.ChildApplication;
import com.example.onboarding.dto.application.AppsResponse;
import com.example.onboarding.dto.application.AppSummaryResponse;
import com.example.onboarding.dto.application.KpiSummary;
import com.example.onboarding.dto.attestation.BulkAttestationRequest;
import com.example.onboarding.dto.attestation.BulkAttestationResponse;
import com.example.onboarding.dto.attestation.IndividualAttestationRequest;
import com.example.onboarding.dto.attestation.IndividualAttestationResponse;
import com.example.onboarding.dto.attestation.AttestationErrorResponse;
import com.example.onboarding.dto.profile.ProfileSnapshot;
import com.example.onboarding.model.Application;
import com.example.onboarding.service.application.ApplicationQueryService;
import com.example.onboarding.service.application.ApplicationManagementService;
import com.example.onboarding.service.application.KpiService;
import com.example.onboarding.service.evidence.EvidenceFieldLinkService;
import com.example.onboarding.service.profile.AutoProfileService;
import com.example.onboarding.config.AutoProfileProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import dev.controlplane.auditkit.annotations.Audited;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/apps")
public class ApplicationController {

    private final AutoProfileService autoProfileService;
    private final AutoProfileProperties props;
    private final ApplicationQueryService applicationQueryService;
    private final ApplicationManagementService applicationManagementService;
    private final KpiService kpiService;
    private final EvidenceFieldLinkService evidenceFieldLinkService;

    public ApplicationController(
            AutoProfileService autoProfileService,
            AutoProfileProperties props,
            ApplicationQueryService applicationQueryService,
            ApplicationManagementService applicationManagementService,
            KpiService kpiService,
            EvidenceFieldLinkService evidenceFieldLinkService
    ) {
        this.autoProfileService = autoProfileService;
        this.props = props;
        this.applicationQueryService = applicationQueryService;
        this.applicationManagementService = applicationManagementService;
        this.kpiService = kpiService;
        this.evidenceFieldLinkService = evidenceFieldLinkService;
    }

    @GetMapping
    public ResponseEntity<AppsResponse> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String criticality,
            @RequestParam(required = false) String applicationType,
            @RequestParam(required = false) String architectureType,
            @RequestParam(required = false) String installType
    ) {
        // Get total count (all apps)
        List<Application> allApps = applicationQueryService.search(Map.of());
        int totalCount = allApps.size();
        
        // Build filter parameters
        Map<String, String> params = Map.of();
        if (search != null && !search.isBlank()) {
            params = new java.util.HashMap<>(params);
            params.put("search", search);
        }
        if (criticality != null && !criticality.isBlank()) {
            params = new java.util.HashMap<>(params);
            params.put("criticality", criticality);
        }
        if (applicationType != null && !applicationType.isBlank()) {
            params = new java.util.HashMap<>(params);
            params.put("applicationType", applicationType);
        }
        if (architectureType != null && !architectureType.isBlank()) {
            params = new java.util.HashMap<>(params);
            params.put("architectureType", architectureType);
        }
        if (installType != null && !installType.isBlank()) {
            params = new java.util.HashMap<>(params);
            params.put("installType", installType);
        }
        
        // Get filtered apps
        List<Application> filteredApps = applicationQueryService.search(params);
        int filteredCount = filteredApps.size();
        
        // Convert to DTOs
        List<AppSummaryResponse> appSummaries = applicationQueryService.convertToSummaryResponse(filteredApps);
        
        // Calculate KPIs from filtered apps
        KpiSummary kpis = kpiService.calculateKpisFromApplications(filteredApps);
        
        AppsResponse response = new AppsResponse(appSummaries, kpis, totalCount, filteredCount);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{appId}")
    public ResponseEntity<com.example.onboarding.dto.application.Application> getApplication(@PathVariable String appId) {
        try {
            com.example.onboarding.dto.application.Application app = applicationManagementService.get(appId);
            return ResponseEntity.ok(app);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{appId}/children")
    public ResponseEntity<List<ChildApplication>> getChildren(@PathVariable String appId) {
        List<ChildApplication> children = applicationManagementService.getChildren(appId);
        return ResponseEntity.ok(children);
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

    /** Bulk attestation endpoint */
    @PostMapping("/{appId}/attestations/bulk")
    public ResponseEntity<BulkAttestationResponse> bulkAttestation(
            @PathVariable String appId,
            @RequestBody BulkAttestationRequest request) {
        
        // Basic validation
        if (request == null) {
            return ResponseEntity.badRequest().build();
        }
        if (request.fields() == null || request.fields().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        if (request.attestedBy() == null || request.attestedBy().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        
        // Validate application exists
        Application app = applicationQueryService.getById(appId);
        if (app == null) {
            return ResponseEntity.notFound().build();
        }
        
        // Process bulk attestations
        BulkAttestationResponse response = evidenceFieldLinkService.processBulkAttestations(appId, request.attestedBy(), request);
        
        return ResponseEntity.ok(response);
    }

    /** Individual attestation endpoint */
    @PostMapping("/{appId}/attestations")
    public ResponseEntity<?> individualAttestation(
            @PathVariable String appId,
            @RequestBody IndividualAttestationRequest request) {
        
        // Basic validation
        if (request == null) {
            return ResponseEntity.badRequest()
                .body(AttestationErrorResponse.badRequest("Request body is required"));
        }
        
        if (!request.isValid()) {
            return ResponseEntity.badRequest()
                .body(AttestationErrorResponse.validationError(
                    "Invalid request: profileFieldId, attestedBy, and attestationType are required", 
                    request));
        }
        
        if (!request.isValidAttestationType()) {
            return ResponseEntity.badRequest()
                .body(AttestationErrorResponse.validationError(
                    "Invalid attestationType. Must be one of: compliance, exception, remediation", 
                    request));
        }
        
        // Validate application exists
        Application app = applicationQueryService.getById(appId);
        if (app == null) {
            return ResponseEntity.status(404)
                .body(AttestationErrorResponse.notFound("Application not found with ID: " + appId));
        }
        
        try {
            // Process individual attestation
            IndividualAttestationResponse response = evidenceFieldLinkService.processIndividualAttestation(appId, request);
            
            // Return appropriate HTTP status based on response
            if ("success".equals(response.status())) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(AttestationErrorResponse.internalError("Unexpected error: " + e.getMessage()));
        }
    }
}
