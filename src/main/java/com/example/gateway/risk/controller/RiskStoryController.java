package com.example.gateway.risk.controller;

import com.example.gateway.application.dto.PageResponse;
import com.example.gateway.risk.dto.AttachEvidenceRequest;
import com.example.gateway.risk.dto.CreateRiskStoryRequest;
import com.example.gateway.risk.dto.RiskStoryResponse;
import com.example.gateway.risk.dto.RiskStoryEvidenceResponse;
import com.example.gateway.risk.service.RiskStoryService;
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
    public ResponseEntity<PageResponse<RiskStoryResponse>> getAppRisks(
            @PathVariable String appId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        PageResponse<RiskStoryResponse> risks = riskStoryService.getRisksByAppIdPaginated(appId, page, pageSize);
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
    public ResponseEntity<PageResponse<RiskStoryResponse>> searchRisks(
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
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        PageResponse<RiskStoryResponse> risks = riskStoryService.searchRisksWithPagination(
            appId, assignedSme, status, domain, derivedFrom, fieldKey, 
            severity, creationType, triggeringEvidenceId, sortBy, sortOrder, page, size);
        return ResponseEntity.ok(risks);
    }
}
