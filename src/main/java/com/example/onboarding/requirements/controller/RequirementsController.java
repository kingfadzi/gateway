package com.example.onboarding.requirements.controller;

import com.example.onboarding.requirements.dto.RequirementsView;
import com.example.onboarding.requirements.service.RequirementsService;
import org.springframework.web.bind.annotation.*;

// src/main/java/com/example/onboarding/web/RequirementsController.java
@RestController
@RequestMapping("/api/apps/{appId}/requirements")
public class RequirementsController {
    private final RequirementsService svc;
    public RequirementsController(RequirementsService svc) { this.svc = svc; }

    @GetMapping
    public RequirementsView get(
            @PathVariable String appId,
            @RequestParam String releaseId,
            @RequestParam(required = false, name = "releaseWindowStartIso") String asOf // optional
    ) {
        return svc.getRequirements(appId, releaseId, asOf);
    }
}
