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

import java.util.Map;

/**
 * DEPRECATED: This controller is deprecated as of V7 migration.
 * All risk_story data has been migrated to the new risk_item architecture.
 *
 * Use the new endpoints instead:
 * - GET /api/v1/risk-items/app/{appId} - Get risks for an application
 * - GET /api/v1/domain-risks/app/{appId} - Get domain-level risk aggregations
 * - POST /api/v1/risk-items - Create manual risk items
 *
 * Migration completed: 383 risk_story records migrated to risk_item.
 * Mapping table available: risk_story_to_item_mapping
 */
@Deprecated(since = "V7", forRemoval = true)
@RestController
@RequestMapping("/api")
public class RiskStoryController {

    private final RiskStoryService riskStoryService;

    public RiskStoryController(RiskStoryService riskStoryService) {
        this.riskStoryService = riskStoryService;
    }

    @Deprecated(since = "V7", forRemoval = true)
    @PostMapping("/apps/{appId}/fields/{fieldKey}/risks")
    public ResponseEntity<Map<String, Object>> createRiskStory(
            @PathVariable String appId,
            @PathVariable String fieldKey,
            @RequestBody CreateRiskStoryRequest request) {
        return ResponseEntity.status(HttpStatus.GONE).body(Map.of(
            "error", "ENDPOINT_DEPRECATED",
            "message", "This endpoint has been deprecated. Risk stories have been migrated to risk items.",
            "migration_date", "V7",
            "replacement_endpoint", "POST /api/v1/risk-items",
            "documentation", "See DomainRiskController and RiskItemController for new API",
            "note", "All 383 risk_story records have been migrated to risk_item. Check risk_story_to_item_mapping for ID mappings."
        ));
    }

    @Deprecated(since = "V7", forRemoval = true)
    @PostMapping("/risks/{riskId}/evidence")
    public ResponseEntity<Map<String, Object>> attachEvidence(
            @PathVariable String riskId,
            @RequestBody AttachEvidenceRequest request) {
        return ResponseEntity.status(HttpStatus.GONE).body(Map.of(
            "error", "ENDPOINT_DEPRECATED",
            "message", "This endpoint has been deprecated. Risk stories have been migrated to risk items.",
            "migration_date", "V7",
            "replacement_endpoint", "Evidence is now linked directly when creating risk items via POST /api/v1/risk-items",
            "documentation", "See RiskItemController for new API",
            "note", "All 383 risk_story records have been migrated to risk_item. Check risk_story_to_item_mapping for ID mappings."
        ));
    }

    @Deprecated(since = "V7", forRemoval = true)
    @DeleteMapping("/risks/{riskId}/evidence/{evidenceId}")
    public ResponseEntity<Map<String, Object>> detachEvidence(
            @PathVariable String riskId,
            @PathVariable String evidenceId) {
        return ResponseEntity.status(HttpStatus.GONE).body(Map.of(
            "error", "ENDPOINT_DEPRECATED",
            "message", "This endpoint has been deprecated. Risk stories have been migrated to risk items.",
            "migration_date", "V7",
            "replacement_endpoint", "Evidence management is now handled through risk_item relationships",
            "documentation", "See RiskItemController for new API",
            "note", "All 383 risk_story records have been migrated to risk_item. Check risk_story_to_item_mapping for ID mappings."
        ));
    }

    @Deprecated(since = "V7", forRemoval = true)
    @GetMapping("/apps/{appId}/risks")
    public ResponseEntity<Map<String, Object>> getAppRisks(
            @PathVariable String appId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ResponseEntity.status(HttpStatus.GONE).body(Map.of(
            "error", "ENDPOINT_DEPRECATED",
            "message", "This endpoint has been deprecated. Risk stories have been migrated to risk items.",
            "migration_date", "V7",
            "replacement_endpoint", "GET /api/v1/risk-items/app/" + appId,
            "documentation", "See RiskItemController for new API",
            "note", "All 383 risk_story records have been migrated to risk_item. Check risk_story_to_item_mapping for ID mappings."
        ));
    }

    @Deprecated(since = "V7", forRemoval = true)
    @GetMapping("/risks/{riskId}")
    public ResponseEntity<Map<String, Object>> getRiskById(@PathVariable String riskId) {
        return ResponseEntity.status(HttpStatus.GONE).body(Map.of(
            "error", "ENDPOINT_DEPRECATED",
            "message", "This endpoint has been deprecated. Risk stories have been migrated to risk items.",
            "migration_date", "V7",
            "replacement_endpoint", "GET /api/v1/risk-items/{riskItemId}",
            "documentation", "See RiskItemController for new API",
            "note", "All 383 risk_story records have been migrated to risk_item. Check risk_story_to_item_mapping for old_risk_id → new_risk_item_id mappings.",
            "lookup_query", "SELECT new_risk_item_id FROM risk_story_to_item_mapping WHERE old_risk_id = '" + riskId + "'"
        ));
    }

    @Deprecated(since = "V7", forRemoval = true)
    @GetMapping("/apps/{appId}/fields/{fieldKey}/risks")
    public ResponseEntity<Map<String, Object>> getRisksByFieldKey(
            @PathVariable String appId,
            @PathVariable String fieldKey) {
        return ResponseEntity.status(HttpStatus.GONE).body(Map.of(
            "error", "ENDPOINT_DEPRECATED",
            "message", "This endpoint has been deprecated. Risk stories have been migrated to risk items.",
            "migration_date", "V7",
            "replacement_endpoint", "GET /api/v1/risk-items/field/" + fieldKey,
            "documentation", "See RiskItemController for new API",
            "note", "All 383 risk_story records have been migrated to risk_item. Filter by appId on the client side if needed."
        ));
    }

    @Deprecated(since = "V7", forRemoval = true)
    @GetMapping("/profile-fields/{profileFieldId}/risks")
    public ResponseEntity<Map<String, Object>> getRisksByProfileFieldId(@PathVariable String profileFieldId) {
        return ResponseEntity.status(HttpStatus.GONE).body(Map.of(
            "error", "ENDPOINT_DEPRECATED",
            "message", "This endpoint has been deprecated. Risk stories have been migrated to risk items.",
            "migration_date", "V7",
            "replacement_endpoint", "GET /api/v1/risk-items/app/{appId} (filter by profileFieldId on client side)",
            "documentation", "See RiskItemController for new API",
            "note", "All 383 risk_story records have been migrated to risk_item. Profile field filtering available via app-based queries."
        ));
    }

    @Deprecated(since = "V7", forRemoval = true)
    @GetMapping("/risks/search")
    public ResponseEntity<Map<String, Object>> searchRisks(
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

        return ResponseEntity.status(HttpStatus.GONE).body(Map.of(
            "error", "ENDPOINT_DEPRECATED",
            "message", "This endpoint has been deprecated. Risk stories have been migrated to risk items.",
            "migration_date", "V7",
            "replacement_endpoints", Map.of(
                "by_app", "GET /api/v1/risk-items/app/{appId}",
                "by_field", "GET /api/v1/risk-items/field/{fieldKey}",
                "by_evidence", "GET /api/v1/risk-items/evidence/{evidenceId}",
                "by_status", "GET /api/v1/risk-items/app/{appId}/status/{status}",
                "domain_view", "GET /api/v1/domain-risks/arb/{arbName}"
            ),
            "documentation", "See RiskItemController and DomainRiskController for new API",
            "note", "All 383 risk_story records have been migrated to risk_item. The assignedSme field is now replaced by ARB assignment at the domain level."
        ));
    }
}
