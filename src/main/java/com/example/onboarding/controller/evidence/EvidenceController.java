package com.example.onboarding.controller.evidence;

import com.example.onboarding.dto.evidence.CreateEvidenceRequest;
import com.example.onboarding.dto.evidence.EvidenceDto;
import com.example.onboarding.dto.evidence.ReviewEvidenceRequest;
import com.example.onboarding.dto.evidence.ReviewEvidenceResponse;
import com.example.onboarding.service.evidence.EvidenceService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

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

    @PostMapping("/api/evidence/{evidenceId}/review")
    public ResponseEntity<ReviewEvidenceResponse> review(
            @PathVariable String evidenceId,
            @RequestBody ReviewEvidenceRequest req
    ) {
        return ResponseEntity.ok(service.review(evidenceId, req));
    }

    @GetMapping("/apps/{appId}/evidence")
    public ResponseEntity<List<EvidenceDto>> list(
            @PathVariable String appId,
            @RequestParam("fieldKey") String fieldKey
    ) {
        return ResponseEntity.ok(service.listByAppAndField(appId, fieldKey));
    }


}
