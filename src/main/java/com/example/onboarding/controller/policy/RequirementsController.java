// src/main/java/com/example/onboarding/requirements/RequirementsController.java
package com.example.onboarding.controller.policy;

import com.example.onboarding.service.policy.RequirementsService;
import com.example.onboarding.dto.policy.RequirementsView;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/apps")
public class RequirementsController {

    private final RequirementsService requirementsService;

    public RequirementsController(RequirementsService requirementsService) {
        this.requirementsService = requirementsService;
    }

    /**
     * Read-only requirements endpoint (Chunk 2).
     * Builds OPA input from the application's profile fields (no query-based ratings),
     * calls OPA, maps to FE shape, and decorates with reuse where applicable.
     *
     * GET /api/apps/{appId}/requirements?releaseId=REL-001&releaseWindowStartIso=2025-09-01T10:00:00Z
     */
    @GetMapping("/{appId}/requirements")
    public RequirementsView getRequirements(
            @PathVariable String appId,
            @RequestParam String releaseId,
            @RequestParam(required = false) String releaseWindowStartIso
    ) {
        return requirementsService.getRequirements(appId, releaseId, releaseWindowStartIso);
    }
}
