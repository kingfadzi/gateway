package com.example.onboarding.controller.evidence;

import com.example.onboarding.dto.evidence.ReuseCandidate;
import com.example.onboarding.service.evidence.EvidenceReuseService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.Optional;

@RestController
@RequestMapping("/internal/evidence")
public class InternalEvidenceController {

    private final EvidenceReuseService svc;

    public InternalEvidenceController(EvidenceReuseService svc) {
        this.svc = svc;
    }

    @GetMapping("/reuse")
    public Optional<ReuseCandidate> reuse(
            @RequestParam String appId,
            @RequestParam String profileField,
            @RequestParam(required = false) Integer maxAgeDays,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime asOf
    ) {
        return svc.findBestReusable(appId, profileField, maxAgeDays, asOf);
    }
}
