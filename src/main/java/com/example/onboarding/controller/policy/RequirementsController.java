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

    @GetMapping("/{appId}/requirements")
    public RequirementsView getRequirements(
            @PathVariable String appId,
            @RequestParam(required = false) String releaseId,
            @RequestParam(required = false) String releaseWindowStartIso,
            // For now, accept inputs via query params; later hydrate from profile DB
            @RequestParam(defaultValue = "B") String criticality,
            @RequestParam(defaultValue = "A1") String security,
            @RequestParam(defaultValue = "B") String integrity,
            @RequestParam(defaultValue = "B") String availability,
            @RequestParam(defaultValue = "B") String resilience,
            @RequestParam(defaultValue = "true") boolean hasDependencies
    ) {
        return requirementsService.getRequirements(
                appId, releaseId, releaseWindowStartIso,
                criticality, security, integrity, availability, resilience, hasDependencies
        );
    }
}
