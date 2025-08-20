package com.example.onboarding.controller.policy;

import com.example.onboarding.dto.evidence.CreateEvidenceRequest;
import com.example.onboarding.dto.evidence.EvidenceDto;
import com.example.onboarding.service.policy.EvidenceService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class EvidenceController {

    private final EvidenceService service;

    public EvidenceController(EvidenceService service) {
        this.service = service;
    }

    // JSON (URI/link evidence)
    @PostMapping(path = "/api/apps/{appId}/evidence", consumes = MediaType.APPLICATION_JSON_VALUE)
    public EvidenceDto createJson(@PathVariable String appId,
                                  @RequestBody CreateEvidenceRequest req) throws Exception {
        return service.createOrDedup(appId, req, null);
    }

    // Multipart (file evidence + meta)
    @PostMapping(path = "/api/apps/{appId}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EvidenceDto createMultipart(@PathVariable String appId,
                                       @RequestPart("meta") CreateEvidenceRequest req,
                                       @RequestPart("file") MultipartFile file) throws Exception {
        return service.createOrDedup(appId, req, file);
    }

    // Debug / readback
    @GetMapping("/internal/evidence/{id}")
    public EvidenceDto get(@PathVariable String id) {
        return service.get(id);
    }
}
