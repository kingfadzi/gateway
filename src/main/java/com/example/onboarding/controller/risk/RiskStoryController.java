package com.example.onboarding.controller.risk;

import com.example.onboarding.dto.risk.AttachEvidenceRequest;
import com.example.onboarding.dto.risk.CreateRiskStoryRequest;
import com.example.onboarding.dto.risk.RiskStoryResponse;
import com.example.onboarding.dto.risk.RiskStoryEvidenceResponse;
import com.example.onboarding.service.risk.RiskStoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class RiskStoryController {

    private final RiskStoryService riskStoryService;

    public RiskStoryController(RiskStoryService riskStoryService) {
        this.riskStoryService = riskStoryService;
    }

    @PostMapping("/apps/{appId}/fields/{fieldKey}/risks")
    public ResponseEntity<RiskStoryResponse> createRiskStory(
            @PathVariable String appId,
            @PathVariable String fieldKey,
            @RequestBody CreateRiskStoryRequest request) {
        RiskStoryResponse response = riskStoryService.createRiskStory(appId, fieldKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/risks/{riskId}/evidence")
    public ResponseEntity<RiskStoryEvidenceResponse> attachEvidence(
            @PathVariable String riskId,
            @RequestBody AttachEvidenceRequest request) {
        RiskStoryEvidenceResponse response = riskStoryService.attachEvidence(riskId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/risks/{riskId}/evidence/{evidenceId}")
    public ResponseEntity<Void> detachEvidence(
            @PathVariable String riskId,
            @PathVariable String evidenceId) {
        riskStoryService.detachEvidence(riskId, evidenceId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/apps/{appId}/risks")
    public ResponseEntity<java.util.List<RiskStoryResponse>> getAppRisks(@PathVariable String appId) {
        java.util.List<RiskStoryResponse> risks = riskStoryService.getRisksByAppId(appId);
        return ResponseEntity.ok(risks);
    }
    
    @GetMapping("/risks/{riskId}")
    public ResponseEntity<RiskStoryResponse> getRiskById(@PathVariable String riskId) {
        RiskStoryResponse risk = riskStoryService.getRiskById(riskId);
        return ResponseEntity.ok(risk);
    }
    
    @GetMapping("/apps/{appId}/fields/{fieldKey}/risks")
    public ResponseEntity<java.util.List<RiskStoryResponse>> getRisksByFieldKey(
            @PathVariable String appId,
            @PathVariable String fieldKey) {
        java.util.List<RiskStoryResponse> risks = riskStoryService.getRisksByAppIdAndFieldKey(appId, fieldKey);
        return ResponseEntity.ok(risks);
    }
    
    @GetMapping("/profile-fields/{profileFieldId}/risks")
    public ResponseEntity<java.util.List<RiskStoryResponse>> getRisksByProfileFieldId(@PathVariable String profileFieldId) {
        java.util.List<RiskStoryResponse> risks = riskStoryService.getRisksByProfileFieldId(profileFieldId);
        return ResponseEntity.ok(risks);
    }
    
    @GetMapping("/risks/search")
    public ResponseEntity<java.util.List<RiskStoryResponse>> searchRisks(
            @RequestParam(required = false) String appId,
            @RequestParam(required = false) String assignedSme,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String derivedFrom,
            @RequestParam(required = false) String fieldKey,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String creationType,
            @RequestParam(required = false) String triggeringEvidenceId,
            @RequestParam(defaultValue = "assignedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortOrder,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        
        java.util.List<RiskStoryResponse> risks = riskStoryService.searchRisks(
            appId, assignedSme, status, domain, derivedFrom, fieldKey, 
            severity, creationType, triggeringEvidenceId, sortBy, sortOrder, page, size);
        return ResponseEntity.ok(risks);
    }
}
