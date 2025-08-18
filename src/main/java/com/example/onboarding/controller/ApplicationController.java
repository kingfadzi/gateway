package com.example.onboarding.controller;

import com.example.onboarding.dto.*;
import com.example.onboarding.service.ApplicationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/apps")
public class ApplicationController {

    private final ApplicationService svc;

    public ApplicationController(ApplicationService svc) {
        this.svc = svc;
    }

    @GetMapping
    public PageResponse<ApplicationDto> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String owner_id,
            @RequestParam(required = false) String onboarding_status,
            @RequestParam(required = false) String operational_status,
            @RequestParam(required = false) String parent_app_id,
            @RequestParam(required = false, defaultValue = "-updated_at") String sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name="page_size", defaultValue = "25") int pageSize
    ) {
        return svc.list(q, owner_id, onboarding_status, operational_status, parent_app_id, sort, page, pageSize);
    }

    @GetMapping("/{appId}")
    public ApplicationDto get(@PathVariable String appId) {
        return svc.get(appId);
    }

    @PostMapping
    public ResponseEntity<ApplicationDto> create(@RequestBody CreateAppRequest req) {
        ApplicationDto created = svc.create(req);
        return ResponseEntity.status(201).body(created);
    }

    @PatchMapping("/{appId}")
    public ApplicationDto patch(@PathVariable String appId, @RequestBody UpdateAppRequest req) {
        return svc.patch(appId, req);
    }

    @DeleteMapping("/{appId}")
    public ResponseEntity<Void> delete(@PathVariable String appId,
                                       @RequestParam(defaultValue = "true") boolean soft) {
        svc.delete(appId, soft);
        return ResponseEntity.noContent().build();
    }
}
