// src/main/java/com/example/onboarding/requirements/RequirementsController.java
package com.example.onboarding.controller;

import com.example.onboarding.opa.OpaClient;
import com.example.onboarding.dto.policy.OpaRequest;
import com.example.onboarding.dto.policy.PolicyDecision;
import com.example.onboarding.dto.policy.PolicyInput;
import com.example.onboarding.repository.policy.RequirementsMapper;
import com.example.onboarding.view.RequirementsView;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/apps")
public class RequirementsController {

    private final OpaClient opaClient;

    public RequirementsController(OpaClient opaClient) {
        this.opaClient = opaClient;
    }

    @GetMapping("/{appId}/requirements")
    public RequirementsView getRequirements(
            @PathVariable String appId,
            @RequestParam(required = false) String releaseId,
            @RequestParam(required = false) String releaseWindowStartIso,
            // For Chunk 2, accept classification inputs via query params
            @RequestParam(defaultValue = "B") String criticality,
            @RequestParam(defaultValue = "A1") String security,
            @RequestParam(defaultValue = "B") String integrity,
            @RequestParam(defaultValue = "B") String availability,
            @RequestParam(defaultValue = "B") String resilience,
            @RequestParam(defaultValue = "true") boolean hasDependencies
    ) {
        var input = new PolicyInput(
                new PolicyInput.App(appId),
                criticality, security, integrity, availability, resilience,
                hasDependencies,
                (releaseId == null ? null : new PolicyInput.Release(releaseId, releaseWindowStartIso))
        );
        PolicyDecision decision = opaClient.evaluate(new OpaRequest(input));
        return RequirementsMapper.toView(decision, releaseWindowStartIso);
    }
}
