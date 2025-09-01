package com.example.onboarding.controller.sme;

import com.example.onboarding.dto.sme.SmeRiskQueueResponse;
import com.example.onboarding.service.sme.SmeRiskService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class SmeRiskController {
    
    private final SmeRiskService smeRiskService;
    
    public SmeRiskController(SmeRiskService smeRiskService) {
        this.smeRiskService = smeRiskService;
    }
    
    /**
     * My Review Queue
     * GET /api/sme/{smeId}/risks/queue
     * Purpose: Get risks pending SME review assigned to current user
     */
    @GetMapping("/sme/{smeId}/risks/queue")
    public ResponseEntity<List<SmeRiskQueueResponse>> getMyReviewQueue(@PathVariable String smeId) {
        List<SmeRiskQueueResponse> queue = smeRiskService.getMyReviewQueue(smeId);
        return ResponseEntity.ok(queue);
    }
    
}