package com.example.onboarding.application.controller;

import com.example.onboarding.application.dto.CreateAppByIdRequest;
import com.example.onboarding.application.dto.Application;
import com.example.onboarding.application.dto.ChildApplication;
import com.example.onboarding.application.dto.AppsResponse;
import com.example.onboarding.application.dto.AppSummaryResponse;
import com.example.onboarding.application.dto.KpiSummary;
import com.example.onboarding.application.dto.attestation.BulkAttestationRequest;
import com.example.onboarding.application.dto.attestation.BulkAttestationResponse;
import com.example.onboarding.application.dto.attestation.IndividualAttestationRequest;
import com.example.onboarding.application.dto.attestation.IndividualAttestationResponse;
import com.example.onboarding.application.dto.attestation.AttestationErrorResponse;
import com.example.onboarding.profile.dto.ProfileSnapshot;
import com.example.onboarding.application.service.ApplicationQueryService;
import com.example.onboarding.application.service.ApplicationManagementService;
import com.example.onboarding.application.service.KpiService;
import com.example.onboarding.evidence.service.EvidenceFieldLinkService;
import com.example.onboarding.profile.service.AutoProfileService;
import com.example.onboarding.config.AutoProfileProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/apps")
@Tag(name = "Applications", description = "Application management API")
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
    @Operation(summary = "Search applications", description = "Search and filter applications with various criteria")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Applications retrieved successfully")
    })
    public ResponseEntity<AppsResponse> search(
            @Parameter(description = "Search term for application name or description")
            @RequestParam(required = false) String search,
            @Parameter(description = "Filter by application criticality")
            @RequestParam(required = false) String criticality,
            @Parameter(description = "Filter by application type")
            @RequestParam(required = false) String applicationType,
            @Parameter(description = "Filter by architecture type")
            @RequestParam(required = false) String architectureType,
            @Parameter(description = "Filter by installation type")
            @RequestParam(required = false) String installType
    ) {
        // Get total count (all apps)
        List<com.example.onboarding.application.model.Application> allApps = applicationQueryService.search(Map.of());
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
        List<com.example.onboarding.application.model.Application> filteredApps = applicationQueryService.search(params);
        int filteredCount = filteredApps.size();
        
        // Convert to DTOs
        List<AppSummaryResponse> appSummaries = applicationQueryService.convertToSummaryResponse(filteredApps);
        
        // Calculate KPIs from filtered apps
        KpiSummary kpis = kpiService.calculateKpisFromApplications(filteredApps);
        
        AppsResponse response = new AppsResponse(appSummaries, kpis, totalCount, filteredCount);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{appId}")
    @Operation(summary = "Get application by ID", description = "Retrieve a specific application by its ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Application retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Application not found")
    })
    public ResponseEntity<Application> getApplication(
            @Parameter(description = "Application ID") @PathVariable String appId) {
        try {
            Application app = applicationManagementService.get(appId);
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
    @Operation(summary = "Create new application", description = "Create a new application with auto-profiling")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Application created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    public ResponseEntity<?> create(
            @Parameter(description = "Application creation request") @RequestBody CreateAppByIdRequest req,
            @Parameter(description = "Enable auto-profiling")
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
        com.example.onboarding.application.model.Application app = applicationQueryService.getById(appId);
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
        com.example.onboarding.application.model.Application app = applicationQueryService.getById(appId);
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
