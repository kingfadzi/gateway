# Risk Story Migration Plan

## Executive Summary

**Objective:** Migrate all risk management workflows, logic, and data from the legacy `risk_story` architecture to the new `risk_item` + `domain_risk` architecture.

**Target State:**
- **POs:** Work at risk_item level (evidence-level risks)
- **SMEs/ARBs:** Work at domain_risk level (domain aggregation)
- **Zero dependencies** on risk_story tables, models, services, or APIs

**Impact:** 19 files to modify/remove, 7 REST endpoints to deprecate, 2 database tables to migrate

**Key Requirement:** Preserve rich risk content structure (hypothesis, condition, consequence) in risk_item

---

## Current State Analysis

### RiskStory Architecture (Legacy)

**Purpose:** Manual risk creation workflow where SMEs create narrative risk stories

**Data Model:**
```
risk_story (parent)
├── risk_id (PK)
├── app_id, field_key, profile_field_id
├── Content: title, hypothesis, condition, consequence, control_refs
├── Workflow: triggering_evidence_id, creation_type, assigned_sme, status
├── Meta: severity, raised_by, owner, timestamps
└── policy_requirement_snapshot (JSONB)

risk_story_evidence (junction)
├── risk_id + evidence_id (composite PK)
├── submitted_by, review_status, waiver fields
└── timestamps
```

**Enums:**
- `risk_status`: PENDING_SME_REVIEW, AWAITING_EVIDENCE, UNDER_REVIEW, APPROVED, REJECTED, WAIVED, CLOSED
- `risk_creation_type`: AUTO, MANUAL

**REST API (7 endpoints):**
```
POST   /api/apps/{appId}/fields/{fieldKey}/risks        → Create risk manually
POST   /api/risks/{riskId}/evidence                     → Attach evidence
DELETE /api/risks/{riskId}/evidence/{evidenceId}        → Detach evidence
GET    /api/apps/{appId}/risks?page=1&pageSize=10       → List risks (paginated)
GET    /api/risks/{riskId}                              → Get single risk
GET    /api/apps/{appId}/fields/{fieldKey}/risks        → Risks by field
GET    /api/risks/search?appId=...&status=...           → Advanced search
```

**Service Layer:**
- `RiskStoryService` + `RiskStoryServiceImpl` (222 lines)
- 12 methods: create, attach/detach evidence, list, search, pagination

**Integration Points:**
1. `RiskAutoCreationServiceImpl` (line 35) - DEAD CODE (injected but never used)
2. `ProfileServiceImpl` (lines 201, 343) - ACTIVE USAGE (queries for graph views)

---

### RiskItem + DomainRisk Architecture (New)

**Purpose:** Auto-creation workflow triggered by evidence submission + domain-level aggregation

**Current Data Model:**
```
domain_risk (parent)
├── domain_risk_id (PK)
├── app_id + domain (UNIQUE constraint)
├── Aggregations: total_items, open_items, high_priority_items
├── Priority: priority_score (0-100), overall_priority, overall_severity
├── Workflow: status, assigned_arb
└── timestamps

risk_item (child) - CURRENT
├── risk_item_id (PK)
├── domain_risk_id (FK CASCADE)
├── app_id, field_key, profile_field_id, triggering_evidence_id
├── Content: title, description
├── Priority: priority (enum), priority_score (0-100), evidence_status, severity
├── Workflow: status, creation_type
└── policy_requirement_snapshot (JSONB)
```

**REST API (14 endpoints):**
```
# PO View (Risk Items)
GET    /api/v1/risk-items/app/{appId}                       → All items prioritized
GET    /api/v1/risk-items/app/{appId}/status/{status}       → Filter by status
GET    /api/v1/risk-items/{riskItemId}                      → Get specific item
PATCH  /api/v1/risk-items/{riskItemId}/status               → Update status
GET    /api/v1/risk-items/field/{fieldKey}                  → Items by field
GET    /api/v1/risk-items/evidence/{evidenceId}             → Items by evidence

# ARB View (Domain Risks)
GET    /api/v1/domain-risks/arb/{arbName}                   → Risks for ARB
GET    /api/v1/domain-risks/arb/{arbName}/summary           → Dashboard stats
GET    /api/v1/domain-risks/{domainRiskId}                  → Get specific risk
GET    /api/v1/domain-risks/{domainRiskId}/items            → Drill down to items
GET    /api/v1/domain-risks/app/{appId}                     → All for app
```

---

## Feature Comparison Matrix

| Feature | RiskStory (Legacy) | RiskItem (Current) | Migration Action |
|---------|-------------------|-------------------|------------------|
| **Creation Method** | Manual (POST request) | Auto (evidence trigger) | ✅ Add manual creation endpoint |
| **Rich Content** | hypothesis/condition/consequence | description only | ✅ **ADD 3 columns to risk_item** |
| **Evidence Attachment** | risk_story_evidence junction | evidence_field_link (field-level) | ✅ Keep field-level approach |
| **Priority Scoring** | Manual severity string | Dynamic score (0-100) with multipliers | ✅ Keep new scoring |
| **Aggregation** | None (flat list) | Domain-level with auto-recalc | ✅ Keep domain aggregation |
| **ARB Assignment** | Manual `assigned_sme` | Auto-routed via domain | ✅ Keep auto-routing |
| **Status Workflow** | 7 states | 5 states | ✅ Map old→new states |
| **Search/Filter** | Complex 8-param search | Basic filters | ✅ Add full search |
| **Profile Integration** | ProfileService queries risk_story | Should query risk_item | ✅ Update ProfileServiceImpl |

---

## Migration Strategy

### Phase 1: Add Missing Columns to risk_item

**Goal:** Ensure risk_item can store all RiskStory content

#### 1.1 Database Migration: Add Rich Content Columns

**Location:** `src/main/resources/db/migration/V4__add_rich_content_to_risk_item.sql`

```sql
-- Add rich risk content columns to risk_item table
ALTER TABLE risk_item
    ADD COLUMN IF NOT EXISTS hypothesis TEXT,
    ADD COLUMN IF NOT EXISTS condition TEXT,
    ADD COLUMN IF NOT EXISTS consequence TEXT,
    ADD COLUMN IF NOT EXISTS control_refs TEXT;

-- Add index for full-text search on risk content
CREATE INDEX IF NOT EXISTS idx_risk_item_content_search
    ON risk_item USING gin(
        to_tsvector('english',
            COALESCE(title, '') || ' ' ||
            COALESCE(description, '') || ' ' ||
            COALESCE(hypothesis, '') || ' ' ||
            COALESCE(condition, '') || ' ' ||
            COALESCE(consequence, '')
        )
    );

-- Update risk_item description for existing records (combine into description if needed)
-- This is optional - only if we want to preserve any existing description text
UPDATE risk_item
SET description = COALESCE(description, '')
WHERE description IS NULL;
```

**Rollback:**
```sql
ALTER TABLE risk_item
    DROP COLUMN IF EXISTS hypothesis,
    DROP COLUMN IF EXISTS condition,
    DROP COLUMN IF EXISTS consequence,
    DROP COLUMN IF EXISTS control_refs;

DROP INDEX IF EXISTS idx_risk_item_content_search;
```

#### 1.2 Update RiskItem Model

**Location:** `src/main/java/com/example/gateway/risk/model/RiskItem.java`

**Add fields after `description`:**
```java
@Column(columnDefinition = "TEXT")
private String hypothesis;

@Column(columnDefinition = "TEXT")
private String condition;

@Column(columnDefinition = "TEXT")
private String consequence;

@Column(name = "control_refs", columnDefinition = "TEXT")
private String controlRefs;
```

#### 1.3 Update DTOs

**Location:** `src/main/java/com/example/gateway/risk/dto/RiskItemResponse.java`

**Add to record:**
```java
public record RiskItemResponse(
    String riskItemId,
    String appId,
    String fieldKey,
    String profileFieldId,
    String triggeringEvidenceId,
    String title,
    String description,
    String hypothesis,      // ✅ NEW
    String condition,       // ✅ NEW
    String consequence,     // ✅ NEW
    String controlRefs,     // ✅ NEW
    RiskPriority priority,
    Integer priorityScore,
    String evidenceStatus,
    // ... rest of fields
) {
    public static RiskItemResponse fromEntity(RiskItem entity) {
        return new RiskItemResponse(
            entity.getRiskItemId(),
            entity.getAppId(),
            entity.getFieldKey(),
            entity.getProfileFieldId(),
            entity.getTriggeringEvidenceId(),
            entity.getTitle(),
            entity.getDescription(),
            entity.getHypothesis(),      // ✅ NEW
            entity.getCondition(),       // ✅ NEW
            entity.getConsequence(),     // ✅ NEW
            entity.getControlRefs(),     // ✅ NEW
            entity.getPriority(),
            entity.getPriorityScore(),
            entity.getEvidenceStatus(),
            // ... rest of mappings
        );
    }
}
```

**Location:** `src/main/java/com/example/gateway/risk/dto/UpdateRiskItemRequest.java`

**Add optional fields:**
```java
public record UpdateRiskItemRequest(
    RiskItemStatus status,
    RiskResolution resolution,
    String resolutionComment,
    String hypothesis,      // ✅ NEW (optional update)
    String condition,       // ✅ NEW (optional update)
    String consequence,     // ✅ NEW (optional update)
    String controlRefs      // ✅ NEW (optional update)
) {}
```

---

### Phase 2: Add Manual Risk Creation

**Goal:** Support manual SME-initiated risk creation (preserve RiskStory capability)

#### 2.1 Create Request DTO

**Location:** `src/main/java/com/example/gateway/risk/dto/CreateRiskItemRequest.java` (NEW)

```java
package com.example.gateway.risk.dto;

import com.example.gateway.risk.model.RiskPriority;
import java.util.Map;

public record CreateRiskItemRequest(
    String appId,
    String fieldKey,
    String profileFieldId,
    String title,
    String description,
    String hypothesis,
    String condition,
    String consequence,
    String controlRefs,
    RiskPriority priority,      // Default: MEDIUM
    String raisedBy,
    String triggeringEvidenceId  // Optional for manual creation
) {
    // Validation helper
    public boolean isValid() {
        return appId != null && !appId.isBlank()
            && fieldKey != null && !fieldKey.isBlank()
            && title != null && !title.isBlank()
            && raisedBy != null && !raisedBy.isBlank();
    }
}
```

#### 2.2 Add Controller Endpoint

**Location:** `src/main/java/com/example/gateway/risk/controller/RiskItemController.java`

**Add after existing endpoints:**
```java
@PostMapping
public ResponseEntity<RiskItemResponse> createRiskItem(
        @RequestBody CreateRiskItemRequest request) {

    log.info("Manual risk item creation requested for app={}, field={}, by={}",
            request.appId(), request.fieldKey(), request.raisedBy());

    if (!request.isValid()) {
        log.warn("Invalid create risk item request: {}", request);
        return ResponseEntity.badRequest().build();
    }

    RiskItemResponse response = riskItemService.createManualRiskItem(request);

    log.info("Risk item created manually: ID={}, App={}, Field={}",
            response.riskItemId(), request.appId(), request.fieldKey());

    return ResponseEntity.status(HttpStatus.CREATED).body(response);
}
```

#### 2.3 Add Service Method

**Location:** `src/main/java/com/example/gateway/risk/service/RiskItemService.java` (interface)

```java
RiskItemResponse createManualRiskItem(CreateRiskItemRequest request);
```

**Location:** `src/main/java/com/example/gateway/risk/service/RiskItemServiceImpl.java`

```java
@Override
@Transactional
public RiskItemResponse createManualRiskItem(CreateRiskItemRequest request) {
    // Get or create domain risk for this app+field
    String derivedFrom = profileFieldRegistryService.getFieldTypeInfo(request.fieldKey())
            .map(ProfileFieldTypeInfo::derivedFrom)
            .orElseThrow(() -> new IllegalArgumentException("Unknown field: " + request.fieldKey()));

    DomainRisk domainRisk = aggregationService.getOrCreateDomainRisk(request.appId(), derivedFrom);

    // Create risk item
    RiskItem riskItem = new RiskItem();
    riskItem.setRiskItemId("item_" + UUID.randomUUID());
    riskItem.setAppId(request.appId());
    riskItem.setFieldKey(request.fieldKey());
    riskItem.setProfileFieldId(request.profileFieldId());
    riskItem.setTriggeringEvidenceId(request.triggeringEvidenceId());

    // Rich content
    riskItem.setTitle(request.title());
    riskItem.setDescription(request.description());
    riskItem.setHypothesis(request.hypothesis());
    riskItem.setCondition(request.condition());
    riskItem.setConsequence(request.consequence());
    riskItem.setControlRefs(request.controlRefs());

    // Priority (default to MEDIUM if not specified)
    RiskPriority priority = request.priority() != null ? request.priority() : RiskPriority.MEDIUM;
    riskItem.setPriority(priority);

    // Calculate priority score (use "missing" status for manual risks initially)
    int priorityScore = priorityCalculator.calculatePriorityScore(priority, "missing");
    riskItem.setPriorityScore(priorityScore);
    riskItem.setEvidenceStatus("missing");
    riskItem.setSeverity(priorityCalculator.getSeverityLabel(priorityScore));

    // Status
    riskItem.setStatus(RiskItemStatus.OPEN);
    riskItem.setCreationType(RiskCreationType.MANUAL_SME_INITIATED);
    riskItem.setRaisedBy(request.raisedBy());
    riskItem.setOpenedAt(OffsetDateTime.now());

    // Add to domain risk (saves item + recalculates aggregations)
    aggregationService.addRiskItemToDomain(domainRisk, riskItem);

    log.info("Manual risk item created: ID={}, Domain={}, Priority={}, Score={}",
            riskItem.getRiskItemId(), domainRisk.getDomain(), priority, priorityScore);

    return RiskItemResponse.fromEntity(riskItem);
}
```

---

### Phase 3: Add Full Search/Filter

**Goal:** Match RiskStory search capabilities

#### 3.1 Add Search Endpoint

**Location:** `src/main/java/com/example/gateway/risk/controller/RiskItemController.java`

```java
@GetMapping("/search")
public ResponseEntity<PageResponse<RiskItemResponse>> searchRiskItems(
        @RequestParam(required = false) String appId,
        @RequestParam(required = false) String fieldKey,
        @RequestParam(required = false) RiskItemStatus status,
        @RequestParam(required = false) RiskPriority priority,
        @RequestParam(required = false) String evidenceStatus,
        @RequestParam(required = false) String domain,
        @RequestParam(required = false) RiskCreationType creationType,
        @RequestParam(required = false) String triggeringEvidenceId,
        @RequestParam(defaultValue = "priority_score") String sortBy,
        @RequestParam(defaultValue = "desc") String sortOrder,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int pageSize) {

    PageResponse<RiskItemResponse> results = riskItemService.searchRiskItems(
            appId, fieldKey, status, priority, evidenceStatus, domain,
            creationType, triggeringEvidenceId, sortBy, sortOrder, page, pageSize);

    return ResponseEntity.ok(results);
}
```

#### 3.2 Add Repository Method

**Location:** `src/main/java/com/example/gateway/risk/repository/RiskItemRepository.java`

```java
@Query(value = """
    SELECT ri.*, dr.domain, dr.assigned_arb,
           app.name as app_name, app.app_criticality_assessment
    FROM risk_item ri
    JOIN domain_risk dr ON ri.domain_risk_id = dr.domain_risk_id
    JOIN application app ON ri.app_id = app.app_id
    WHERE (:appId IS NULL OR ri.app_id = :appId)
      AND (:fieldKey IS NULL OR ri.field_key = :fieldKey)
      AND (:status IS NULL OR ri.status = CAST(:status AS text))
      AND (:priority IS NULL OR ri.priority = CAST(:priority AS text))
      AND (:evidenceStatus IS NULL OR ri.evidence_status = :evidenceStatus)
      AND (:domain IS NULL OR dr.domain = :domain)
      AND (:creationType IS NULL OR ri.creation_type = CAST(:creationType AS text))
      AND (:triggeringEvidenceId IS NULL OR ri.triggering_evidence_id = :triggeringEvidenceId)
    ORDER BY
      CASE WHEN :sortBy = 'priority_score' AND :sortOrder = 'desc' THEN ri.priority_score END DESC,
      CASE WHEN :sortBy = 'priority_score' AND :sortOrder = 'asc' THEN ri.priority_score END ASC,
      CASE WHEN :sortBy = 'created_at' AND :sortOrder = 'desc' THEN ri.created_at END DESC,
      CASE WHEN :sortBy = 'created_at' AND :sortOrder = 'asc' THEN ri.created_at END ASC,
      ri.priority_score DESC
    LIMIT :limit OFFSET :offset
    """, nativeQuery = true)
List<Map<String, Object>> searchRiskItems(
    @Param("appId") String appId,
    @Param("fieldKey") String fieldKey,
    @Param("status") String status,
    @Param("priority") String priority,
    @Param("evidenceStatus") String evidenceStatus,
    @Param("domain") String domain,
    @Param("creationType") String creationType,
    @Param("triggeringEvidenceId") String triggeringEvidenceId,
    @Param("sortBy") String sortBy,
    @Param("sortOrder") String sortOrder,
    @Param("limit") int limit,
    @Param("offset") int offset
);

@Query(value = """
    SELECT COUNT(*)
    FROM risk_item ri
    JOIN domain_risk dr ON ri.domain_risk_id = dr.domain_risk_id
    WHERE (:appId IS NULL OR ri.app_id = :appId)
      AND (:fieldKey IS NULL OR ri.field_key = :fieldKey)
      AND (:status IS NULL OR ri.status = CAST(:status AS text))
      AND (:priority IS NULL OR ri.priority = CAST(:priority AS text))
      AND (:evidenceStatus IS NULL OR ri.evidence_status = :evidenceStatus)
      AND (:domain IS NULL OR dr.domain = :domain)
      AND (:creationType IS NULL OR ri.creation_type = CAST(:creationType AS text))
      AND (:triggeringEvidenceId IS NULL OR ri.triggering_evidence_id = :triggeringEvidenceId)
    """, nativeQuery = true)
long countSearchResults(
    @Param("appId") String appId,
    @Param("fieldKey") String fieldKey,
    @Param("status") String status,
    @Param("priority") String priority,
    @Param("evidenceStatus") String evidenceStatus,
    @Param("domain") String domain,
    @Param("creationType") String creationType,
    @Param("triggeringEvidenceId") String triggeringEvidenceId
);
```

---

### Phase 4: Profile Service Migration

**Goal:** Update ProfileServiceImpl to query risk_item instead of risk_story

#### 4.1 Update Dependencies

**Location:** `src/main/java/com/example/gateway/profile/service/ProfileServiceImpl.java`

**Remove:**
```java
private final RiskStoryRepository riskStoryRepository;  // ❌ REMOVE line 39

public ProfileServiceImpl(...,
                         RiskStoryRepository riskStoryRepository,  // ❌ REMOVE line 48
                         ...) {
    this.riskStoryRepository = riskStoryRepository;  // ❌ REMOVE line 56
}
```

**Add:**
```java
private final RiskItemRepository riskItemRepository;  // ✅ ADD

public ProfileServiceImpl(...,
                         RiskItemRepository riskItemRepository,  // ✅ ADD
                         ...) {
    this.riskItemRepository = riskItemRepository;  // ✅ ADD
}
```

#### 4.2 Update getProfileDomainGraph() Method

**Location:** ProfileServiceImpl.java:200-260

**Replace lines 200-205:**
```java
// OLD CODE (REMOVE):
// Get risk stories for this application
List<RiskStory> riskStories = riskStoryRepository.findByAppId(appId);

// Group risks by field key
Map<String, List<RiskStory>> risksByFieldKey = riskStories.stream()
    .collect(Collectors.groupingBy(RiskStory::getFieldKey));
```

**With:**
```java
// NEW CODE:
// Get risk items for this application
List<RiskItem> riskItems = riskItemRepository.findByAppId(appId);

// Group risks by field key
Map<String, List<RiskItem>> risksByFieldKey = riskItems.stream()
    .collect(Collectors.groupingBy(RiskItem::getFieldKey));
```

**Replace lines 249-260:**
```java
// OLD CODE (REMOVE):
// Get risks for this field
List<RiskStory> risksForField = risksByFieldKey.getOrDefault(field.fieldKey(), Collections.emptyList());

// Convert risks to graph payload
List<RiskGraphPayload> riskGraphPayloads = risksForField.stream()
    .map(risk -> new RiskGraphPayload(
        risk.getRiskId(),
        risk.getTitle(),
        risk.getSeverity(),
        risk.getStatus().toString()
    ))
    .collect(Collectors.toList());
```

**With:**
```java
// NEW CODE:
// Get risk items for this field
List<RiskItem> riskItemsForField = risksByFieldKey.getOrDefault(field.fieldKey(), Collections.emptyList());

// Convert risk items to graph payload
List<RiskGraphPayload> riskGraphPayloads = riskItemsForField.stream()
    .map(risk -> new RiskGraphPayload(
        risk.getRiskItemId(),
        risk.getTitle(),
        risk.getSeverity(),
        risk.getStatus().toString()
    ))
    .collect(Collectors.toList());
```

#### 4.3 Update getProfileFieldContext() Method

**Location:** ProfileServiceImpl.java:342-355

**Replace lines 342-355:**
```java
// OLD CODE (REMOVE):
// Get risks for this specific field
List<RiskStory> risksForField = riskStoryRepository.findByAppId(appId).stream()
    .filter(risk -> fieldKey.equals(risk.getFieldKey()))
    .collect(Collectors.toList());

// Convert risks to graph payload
List<RiskGraphPayload> riskGraphPayloads = risksForField.stream()
    .map(risk -> new RiskGraphPayload(
        risk.getRiskId(),
        risk.getTitle(),
        risk.getSeverity(),
        risk.getStatus().toString()
    ))
    .collect(Collectors.toList());
```

**With:**
```java
// NEW CODE:
// Get risk items for this specific field
List<RiskItem> riskItemsForField = riskItemRepository.findByFieldKey(fieldKey);

// Convert risk items to graph payload
List<RiskGraphPayload> riskGraphPayloads = riskItemsForField.stream()
    .map(risk -> new RiskGraphPayload(
        risk.getRiskItemId(),
        risk.getTitle(),
        risk.getSeverity(),
        risk.getStatus().toString()
    ))
    .collect(Collectors.toList());
```

**Add missing repository method:**

**Location:** `src/main/java/com/example/gateway/risk/repository/RiskItemRepository.java`

```java
List<RiskItem> findByFieldKey(String fieldKey);
```

---

### Phase 5: Data Migration

**Goal:** Migrate existing risk_story data to risk_item + domain_risk tables

#### 5.1 Create Migration Script

**Location:** `src/main/resources/db/migration/V5__migrate_risk_story_to_risk_item.sql`

```sql
-- ============================================================================
-- V5: Migrate risk_story data to risk_item + domain_risk
-- ============================================================================

-- Step 1: Create domain risks (one per app + domain)
-- ============================================================================
INSERT INTO domain_risk (
    domain_risk_id, app_id, domain, status, assigned_arb,
    total_items, open_items, high_priority_items, priority_score,
    overall_priority, overall_severity,
    created_at, updated_at
)
SELECT
    'dr_' || gen_random_uuid()::text as domain_risk_id,
    rs.app_id,
    COALESCE(pf.derived_from, 'unknown') as domain,
    'PENDING_ARB_REVIEW'::domain_risk_status as status,
    -- Route ARB based on domain
    CASE
        WHEN pf.derived_from = 'security_rating' THEN 'security_arb'
        WHEN pf.derived_from = 'integrity_rating' THEN 'integrity_arb'
        WHEN pf.derived_from = 'availability_rating' THEN 'availability_arb'
        WHEN pf.derived_from = 'resilience_rating' THEN 'resilience_arb'
        WHEN pf.derived_from = 'confidentiality_rating' THEN 'confidentiality_arb'
        WHEN pf.derived_from = 'app_criticality_assessment' THEN 'governance_arb'
        ELSE 'governance_arb'
    END as assigned_arb,
    0 as total_items,  -- Will be updated in step 3
    0 as open_items,
    0 as high_priority_items,
    0 as priority_score,
    'LOW'::risk_priority as overall_priority,
    'Low' as overall_severity,
    MIN(rs.created_at) as created_at,
    MAX(rs.updated_at) as updated_at
FROM risk_story rs
LEFT JOIN profile_field pf ON rs.profile_field_id = pf.id
GROUP BY rs.app_id, pf.derived_from
ON CONFLICT (app_id, domain) DO NOTHING;

-- Step 2: Migrate risk stories to risk items
-- ============================================================================
INSERT INTO risk_item (
    risk_item_id, app_id, field_key, profile_field_id,
    triggering_evidence_id, title, description,
    hypothesis, condition, consequence, control_refs,  -- ✅ Rich content preserved
    priority, priority_score, evidence_status, severity,
    status, resolution, resolution_comment,
    creation_type, raised_by,
    opened_at, resolved_at, closed_at,
    policy_requirement_snapshot, domain_risk_id,
    created_at, updated_at
)
SELECT
    'item_' || gen_random_uuid()::text as risk_item_id,
    rs.app_id,
    rs.field_key,
    rs.profile_field_id,
    rs.triggering_evidence_id,
    rs.title,
    -- Keep description if present, otherwise null
    NULLIF(TRIM(rs.attributes->>'description'), '') as description,
    -- ✅ Preserve rich content structure
    rs.hypothesis,
    rs.condition,
    rs.consequence,
    rs.control_refs,
    -- Map severity string to priority enum
    CASE
        WHEN LOWER(rs.severity) IN ('critical', 'high') THEN 'HIGH'::risk_priority
        WHEN LOWER(rs.severity) = 'medium' THEN 'MEDIUM'::risk_priority
        WHEN LOWER(rs.severity) = 'low' THEN 'LOW'::risk_priority
        ELSE 'MEDIUM'::risk_priority
    END as priority,
    -- Calculate priority score based on severity
    CASE
        WHEN LOWER(rs.severity) IN ('critical', 'high') THEN 80
        WHEN LOWER(rs.severity) = 'medium' THEN 50
        WHEN LOWER(rs.severity) = 'low' THEN 20
        ELSE 50
    END as priority_score,
    -- Default to missing, will be recalculated by evidence status calculation
    'missing' as evidence_status,
    rs.severity,
    -- Map old status enum to new status enum
    CASE
        WHEN rs.status IN ('PENDING_SME_REVIEW', 'AWAITING_EVIDENCE', 'UNDER_REVIEW')
            THEN 'OPEN'::risk_item_status
        WHEN rs.status = 'APPROVED'
            THEN 'RESOLVED'::risk_item_status
        WHEN rs.status IN ('REJECTED', 'WAIVED')
            THEN 'WAIVED'::risk_item_status
        WHEN rs.status = 'CLOSED'
            THEN 'CLOSED'::risk_item_status
        ELSE 'OPEN'::risk_item_status
    END as status,
    -- Map resolution
    CASE
        WHEN rs.status = 'APPROVED' THEN 'REMEDIATED'::risk_resolution
        WHEN rs.status IN ('REJECTED', 'WAIVED') THEN 'RISK_ACCEPTED'::risk_resolution
        WHEN rs.status = 'CLOSED' THEN 'VERIFIED'::risk_resolution
        ELSE NULL
    END as resolution,
    -- Use closure_reason as resolution comment
    rs.closure_reason as resolution_comment,
    -- Map creation type
    CASE
        WHEN rs.creation_type = 'AUTO' THEN 'SYSTEM_AUTO_CREATION'::risk_creation_type
        WHEN rs.creation_type = 'MANUAL' THEN 'MANUAL_SME_INITIATED'::risk_creation_type
        ELSE 'MANUAL_SME_INITIATED'::risk_creation_type
    END as creation_type,
    rs.raised_by,
    rs.opened_at,
    CASE WHEN rs.status = 'APPROVED' THEN rs.reviewed_at ELSE NULL END as resolved_at,
    rs.closed_at,
    rs.policy_requirement_snapshot,
    -- Link to domain risk (match app_id + derived_from)
    (SELECT dr.domain_risk_id
     FROM domain_risk dr
     LEFT JOIN profile_field pf2 ON rs.profile_field_id = pf2.id
     WHERE dr.app_id = rs.app_id
     AND dr.domain = COALESCE(pf2.derived_from, 'unknown')
     LIMIT 1) as domain_risk_id,
    rs.created_at,
    rs.updated_at
FROM risk_story rs
WHERE NOT EXISTS (
    -- Avoid duplicates if migration is run multiple times
    SELECT 1 FROM risk_item ri
    WHERE ri.app_id = rs.app_id
    AND ri.field_key = rs.field_key
    AND ri.created_at = rs.created_at
);

-- Step 3: Recalculate domain risk aggregations
-- ============================================================================
UPDATE domain_risk dr
SET
    total_items = (
        SELECT COUNT(*)
        FROM risk_item ri
        WHERE ri.domain_risk_id = dr.domain_risk_id
    ),
    open_items = (
        SELECT COUNT(*)
        FROM risk_item ri
        WHERE ri.domain_risk_id = dr.domain_risk_id
        AND ri.status IN ('OPEN', 'IN_PROGRESS')
    ),
    high_priority_items = (
        SELECT COUNT(*)
        FROM risk_item ri
        WHERE ri.domain_risk_id = dr.domain_risk_id
        AND ri.priority IN ('CRITICAL', 'HIGH')
    ),
    priority_score = LEAST(100, (
        SELECT COALESCE(MAX(ri.priority_score), 0) +
               -- High priority bonus: min(10, count * 2)
               LEAST(10, (SELECT COUNT(*) FROM risk_item ri2
                         WHERE ri2.domain_risk_id = dr.domain_risk_id
                         AND ri2.priority IN ('CRITICAL', 'HIGH')) * 2) +
               -- Volume bonus: min(5, (open_count - 3))
               GREATEST(0, LEAST(5, (SELECT COUNT(*) FROM risk_item ri3
                                    WHERE ri3.domain_risk_id = dr.domain_risk_id
                                    AND ri3.status IN ('OPEN', 'IN_PROGRESS')) - 3))
        FROM risk_item ri
        WHERE ri.domain_risk_id = dr.domain_risk_id
        AND ri.status IN ('OPEN', 'IN_PROGRESS')
    )),
    overall_priority = (
        CASE
            WHEN (SELECT COALESCE(MAX(ri.priority_score), 0) FROM risk_item ri
                  WHERE ri.domain_risk_id = dr.domain_risk_id) >= 90 THEN 'CRITICAL'::risk_priority
            WHEN (SELECT COALESCE(MAX(ri.priority_score), 0) FROM risk_item ri
                  WHERE ri.domain_risk_id = dr.domain_risk_id) >= 70 THEN 'HIGH'::risk_priority
            WHEN (SELECT COALESCE(MAX(ri.priority_score), 0) FROM risk_item ri
                  WHERE ri.domain_risk_id = dr.domain_risk_id) >= 40 THEN 'MEDIUM'::risk_priority
            ELSE 'LOW'::risk_priority
        END
    ),
    overall_severity = (
        CASE
            WHEN (SELECT COALESCE(MAX(ri.priority_score), 0) FROM risk_item ri
                  WHERE ri.domain_risk_id = dr.domain_risk_id) >= 90 THEN 'Critical'
            WHEN (SELECT COALESCE(MAX(ri.priority_score), 0) FROM risk_item ri
                  WHERE ri.domain_risk_id = dr.domain_risk_id) >= 70 THEN 'High'
            WHEN (SELECT COALESCE(MAX(ri.priority_score), 0) FROM risk_item ri
                  WHERE ri.domain_risk_id = dr.domain_risk_id) >= 40 THEN 'Medium'
            ELSE 'Low'
        END
    ),
    status = (
        CASE
            WHEN (SELECT COUNT(*) FROM risk_item ri
                  WHERE ri.domain_risk_id = dr.domain_risk_id
                  AND ri.status IN ('OPEN', 'IN_PROGRESS')) = 0
            THEN 'RESOLVED'::domain_risk_status
            ELSE 'PENDING_ARB_REVIEW'::domain_risk_status
        END
    ),
    updated_at = NOW()
WHERE EXISTS (SELECT 1 FROM risk_item ri WHERE ri.domain_risk_id = dr.domain_risk_id);

-- Step 4: Create mapping table for reference (optional but recommended)
-- ============================================================================
CREATE TABLE IF NOT EXISTS risk_story_to_item_mapping (
    old_risk_id TEXT PRIMARY KEY,
    new_risk_item_id TEXT NOT NULL,
    app_id TEXT NOT NULL,
    field_key TEXT NOT NULL,
    migrated_at TIMESTAMPTZ DEFAULT NOW()
);

INSERT INTO risk_story_to_item_mapping (old_risk_id, new_risk_item_id, app_id, field_key)
SELECT
    rs.risk_id,
    ri.risk_item_id,
    rs.app_id,
    rs.field_key
FROM risk_story rs
JOIN risk_item ri ON rs.app_id = ri.app_id
    AND rs.field_key = ri.field_key
    AND rs.created_at = ri.created_at
ON CONFLICT (old_risk_id) DO NOTHING;

-- Step 5: Validation queries (for manual verification)
-- ============================================================================
-- These are commented out but can be run manually to verify migration

-- Count comparison
-- SELECT 'risk_story' as table_name, COUNT(*) as count FROM risk_story
-- UNION ALL
-- SELECT 'risk_item', COUNT(*) FROM risk_item
-- UNION ALL
-- SELECT 'migrated_mapping', COUNT(*) FROM risk_story_to_item_mapping;

-- Check for unmigrated risks
-- SELECT COUNT(*) as unmigrated_count, array_agg(rs.risk_id) as unmigrated_ids
-- FROM risk_story rs
-- LEFT JOIN risk_story_to_item_mapping m ON rs.risk_id = m.old_risk_id
-- WHERE m.old_risk_id IS NULL;

-- Verify domain risks
-- SELECT dr.app_id, dr.domain, dr.total_items, dr.open_items,
--        dr.priority_score, dr.assigned_arb
-- FROM domain_risk dr
-- ORDER BY dr.app_id, dr.domain;

-- Sample comparison of rich content
-- SELECT
--     rs.risk_id as old_id,
--     ri.risk_item_id as new_id,
--     rs.title as old_title,
--     ri.title as new_title,
--     rs.hypothesis as old_hypothesis,
--     ri.hypothesis as new_hypothesis,
--     rs.condition as old_condition,
--     ri.condition as new_condition
-- FROM risk_story rs
-- JOIN risk_story_to_item_mapping m ON rs.risk_id = m.old_risk_id
-- JOIN risk_item ri ON m.new_risk_item_id = ri.risk_item_id
-- LIMIT 10;
```

#### 5.2 Rollback Script

**Location:** `src/main/resources/db/migration/U5__rollback_risk_story_migration.sql` (undo)

```sql
-- Rollback V5 migration

-- Delete mapping table
DROP TABLE IF EXISTS risk_story_to_item_mapping;

-- Delete migrated risk items (keep auto-created ones)
DELETE FROM risk_item
WHERE risk_item_id LIKE 'item_%'
AND creation_type = 'MANUAL_SME_INITIATED';

-- Delete created domain risks (only if empty)
DELETE FROM domain_risk
WHERE domain_risk_id LIKE 'dr_%'
AND total_items = 0;

-- Recalculate remaining domain risks
UPDATE domain_risk dr
SET
    total_items = (SELECT COUNT(*) FROM risk_item WHERE domain_risk_id = dr.domain_risk_id),
    open_items = (SELECT COUNT(*) FROM risk_item WHERE domain_risk_id = dr.domain_risk_id AND status IN ('OPEN', 'IN_PROGRESS')),
    updated_at = NOW();

-- risk_story data remains intact for retry
```

---

### Phase 6: Remove Dead Code

**Goal:** Clean up RiskAutoCreationServiceImpl

#### 6.1 Remove Unused RiskStoryRepository

**Location:** `src/main/java/com/example/gateway/risk/service/RiskAutoCreationServiceImpl.java`

**Remove lines 35, 49, 62:**
```java
// LINE 35 - REMOVE:
private final RiskStoryRepository riskStoryRepository;

// LINE 49 - REMOVE from constructor parameter:
RiskStoryRepository riskStoryRepository,

// LINE 62 - REMOVE from constructor body:
this.riskStoryRepository = riskStoryRepository;
```

**Verify:**
```bash
./mvnw clean compile
```

---

### Phase 7: Deprecate RiskStory API

**Goal:** Mark old endpoints as deprecated, add warnings

#### 7.1 Update Controller with Deprecation Warnings

**Location:** `src/main/java/com/example/gateway/risk/controller/RiskStoryController.java`

**Add class-level annotation:**
```java
@Deprecated(since = "2.0", forRemoval = true)
@RestController
@RequestMapping("/api")
public class RiskStoryController {
```

**Add method-level deprecation with redirects:**
```java
@Deprecated(since = "2.0", forRemoval = true)
@PostMapping("/apps/{appId}/fields/{fieldKey}/risks")
public ResponseEntity<?> createRiskStory(...) {
    log.warn("DEPRECATED API CALL: POST /api/apps/{}/fields/{}/risks - Use POST /api/v1/risk-items instead",
             appId, fieldKey);

    return ResponseEntity.status(HttpStatus.GONE)
            .body(Map.of(
                "error", "This endpoint has been deprecated and removed",
                "message", "Please use POST /api/v1/risk-items for manual risk creation",
                "migration_guide", "See RISK_STORY_MIGRATION_PLAN.md",
                "new_endpoint", "/api/v1/risk-items"
            ));
}

@Deprecated(since = "2.0", forRemoval = true)
@GetMapping("/apps/{appId}/risks")
public ResponseEntity<?> getAppRisks(...) {
    log.warn("DEPRECATED API CALL: GET /api/apps/{}/risks - Use GET /api/v1/risk-items/app/{} instead",
             appId, appId);

    return ResponseEntity.status(HttpStatus.GONE)
            .body(Map.of(
                "error", "This endpoint has been deprecated and removed",
                "message", "Please use GET /api/v1/risk-items/app/{appId}",
                "new_endpoint", "/api/v1/risk-items/app/" + appId
            ));
}

// Repeat for all 7 endpoints...
```

#### 7.2 Add Migration Guide

**Location:** Create `RISK_API_MIGRATION_GUIDE.md`

```markdown
# Risk API Migration Guide

## Deprecated Endpoints (Removed)

All `/api/risks/*` and `/api/apps/{appId}/fields/{fieldKey}/risks` endpoints have been removed.

## Migration Mapping

| Old Endpoint (REMOVED) | New Endpoint | Notes |
|------------------------|--------------|-------|
| `POST /api/apps/{appId}/fields/{fieldKey}/risks` | `POST /api/v1/risk-items` | Manual risk creation |
| `GET /api/apps/{appId}/risks` | `GET /api/v1/risk-items/app/{appId}` | List all risks |
| `GET /api/risks/{riskId}` | `GET /api/v1/risk-items/{riskItemId}` | Get single risk |
| `GET /api/apps/{appId}/fields/{fieldKey}/risks` | `GET /api/v1/risk-items/field/{fieldKey}` | Risks by field |
| `GET /api/risks/search` | `GET /api/v1/risk-items/search` | Advanced search |
| `POST /api/risks/{riskId}/evidence` | Not needed | Evidence links to fields |
| `DELETE /api/risks/{riskId}/evidence/{evidenceId}` | Not needed | Evidence links to fields |

## Request/Response Changes

### CreateRiskStoryRequest → CreateRiskItemRequest

**Old:**
```json
{
  "title": "Risk title",
  "hypothesis": "If X happens",
  "condition": "When Y exists",
  "consequence": "Then Z occurs",
  "severity": "high",
  "raisedBy": "sme@example.com",
  "trackId": "track-123"
}
```

**New:**
```json
{
  "appId": "app-123",
  "fieldKey": "encryption_at_rest",
  "profileFieldId": "pf-456",
  "title": "Risk title",
  "hypothesis": "If X happens",
  "condition": "When Y exists",
  "consequence": "Then Z occurs",
  "priority": "HIGH",
  "raisedBy": "sme@example.com"
}
```

**Changes:**
- ✅ `hypothesis`, `condition`, `consequence` preserved
- ✅ `severity` → `priority` (enum: LOW, MEDIUM, HIGH, CRITICAL)
- ✅ Must provide `appId`, `fieldKey`, `profileFieldId`
- ❌ `trackId` removed (not used in new architecture)

### RiskStoryResponse → RiskItemResponse

**Status enum changes:**
- `PENDING_SME_REVIEW` → `OPEN`
- `AWAITING_EVIDENCE` → `OPEN`
- `UNDER_REVIEW` → `IN_PROGRESS`
- `APPROVED` → `RESOLVED`
- `REJECTED` / `WAIVED` → `WAIVED`
- `CLOSED` → `CLOSED`

**New fields:**
- `priorityScore` (0-100 calculated score)
- `evidenceStatus` (drives priority calculation)
- `domainRiskId` (links to parent domain risk)

**Removed fields:**
- `assignedSme` (use domain risk `assignedArb` instead)
- `assignedAt`, `reviewedAt` (not tracked at item level)
- `trackId` (not used)
```

---

### Phase 8: Delete RiskStory Code (Final Cleanup)

**Goal:** Remove all RiskStory code after 2 release cycles

#### 8.1 Delete Service Layer

```bash
rm src/main/java/com/example/gateway/risk/service/RiskStoryService.java
rm src/main/java/com/example/gateway/risk/service/RiskStoryServiceImpl.java
rm src/main/java/com/example/gateway/risk/mapper/RiskStoryRowMapper.java
```

#### 8.2 Delete Repository Layer

```bash
rm src/main/java/com/example/gateway/risk/repository/RiskStoryRepository.java
rm src/main/java/com/example/gateway/risk/repository/RiskStoryEvidenceRepository.java
```

#### 8.3 Delete Model Layer

```bash
rm src/main/java/com/example/gateway/risk/model/RiskStory.java
rm src/main/java/com/example/gateway/risk/model/RiskStoryEvidence.java
rm src/main/java/com/example/gateway/risk/model/RiskStoryEvidenceId.java
# Check if RiskStatus is used elsewhere before deleting!
```

#### 8.4 Delete DTO Layer

```bash
rm src/main/java/com/example/gateway/risk/dto/CreateRiskStoryRequest.java
rm src/main/java/com/example/gateway/risk/dto/RiskStoryResponse.java
rm src/main/java/com/example/gateway/risk/dto/AttachEvidenceRequest.java
rm src/main/java/com/example/gateway/risk/dto/RiskStoryEvidenceResponse.java
```

#### 8.5 Delete Controller

```bash
rm src/main/java/com/example/gateway/risk/controller/RiskStoryController.java
```

#### 8.6 Verify Build

```bash
./mvnw clean compile
./mvnw test
```

---

### Phase 9: Database Cleanup

**Goal:** Drop risk_story tables after all code removed

#### 9.1 Archive Tables First

**Manual SQL (run before Flyway migration):**
```sql
-- Create archive schema
CREATE SCHEMA IF NOT EXISTS archive;

-- Move tables to archive (preserves data)
ALTER TABLE risk_story SET SCHEMA archive;
ALTER TABLE risk_story_evidence SET SCHEMA archive;

-- Add archived timestamp
ALTER TABLE archive.risk_story ADD COLUMN IF NOT EXISTS archived_at TIMESTAMPTZ DEFAULT NOW();
ALTER TABLE archive.risk_story_evidence ADD COLUMN IF NOT EXISTS archived_at TIMESTAMPTZ DEFAULT NOW();

-- Grant read access for auditing
GRANT SELECT ON archive.risk_story TO readonly_user;
GRANT SELECT ON archive.risk_story_evidence TO readonly_user;
```

#### 9.2 Drop Tables via Flyway

**Location:** `src/main/resources/db/migration/V6__drop_risk_story_tables.sql`

```sql
-- ============================================================================
-- V6: Drop risk_story tables (moved to archive schema)
-- ============================================================================

-- Verify tables are archived
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'archive'
        AND table_name = 'risk_story'
    ) THEN
        RAISE NOTICE 'risk_story archived successfully in archive schema';
    ELSE
        RAISE EXCEPTION 'risk_story table not found in archive schema - aborting drop!';
    END IF;
END $$;

-- Drop junction table first (has FK to risk_story)
DROP TABLE IF EXISTS public.risk_story_evidence CASCADE;

-- Drop main table
DROP TABLE IF EXISTS public.risk_story CASCADE;

-- Drop mapping table (no longer needed)
DROP TABLE IF EXISTS public.risk_story_to_item_mapping CASCADE;

-- Clean up enums if not used elsewhere
-- Note: Check usage before dropping!
DO $$
BEGIN
    -- Only drop risk_status if risk_item doesn't use it
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'risk_item'
        AND column_name = 'status'
        AND udt_name = 'risk_status'
    ) THEN
        DROP TYPE IF EXISTS risk_status CASCADE;
        RAISE NOTICE 'Dropped risk_status enum (not used by risk_item)';
    ELSE
        RAISE NOTICE 'Keeping risk_status enum (used by risk_item)';
    END IF;
END $$;

-- Verify cleanup
DO $$
DECLARE
    story_count INTEGER;
    item_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO story_count FROM archive.risk_story;
    SELECT COUNT(*) INTO item_count FROM risk_item WHERE creation_type = 'MANUAL_SME_INITIATED';

    RAISE NOTICE 'Migration complete: % risk_stories archived, % risk_items created', story_count, item_count;

    IF story_count > item_count THEN
        RAISE WARNING 'Archived count (%) > Migrated count (%) - verify migration!', story_count, item_count;
    END IF;
END $$;
```

#### 9.3 Schedule Archive Deletion (90 days later)

**Manual SQL (run after 90 days):**
```sql
-- Final cleanup after 90-day retention period
DROP TABLE IF EXISTS archive.risk_story CASCADE;
DROP TABLE IF EXISTS archive.risk_story_evidence CASCADE;

-- Drop archive schema if empty
DROP SCHEMA IF EXISTS archive CASCADE;
```

---

## Timeline & Milestones

### Sprint 1: Database Schema (1 week)
- [ ] Write V4 migration (add hypothesis/condition/consequence to risk_item)
- [ ] Test on local/dev environment
- [ ] Deploy to staging
- [ ] Verify columns exist

**Deliverable:** risk_item table has rich content columns

---

### Sprint 2: API Feature Parity (2 weeks)
- [ ] Add CreateRiskItemRequest DTO
- [ ] Add POST /api/v1/risk-items endpoint (manual creation)
- [ ] Add GET /api/v1/risk-items/search endpoint (full search)
- [ ] Update RiskItemResponse with rich content fields
- [ ] Add service methods
- [ ] Add repository search queries
- [ ] Write integration tests

**Deliverable:** RiskItem API matches RiskStory capabilities

---

### Sprint 3: Profile Integration (1 week)
- [ ] Update ProfileServiceImpl dependencies
- [ ] Replace riskStoryRepository with riskItemRepository
- [ ] Update getProfileDomainGraph() method
- [ ] Update getProfileFieldContext() method
- [ ] Add RiskItemRepository.findByFieldKey() method
- [ ] Test profile graph views
- [ ] Verify risk data displays correctly

**Deliverable:** Profile API uses risk_item data

---

### Sprint 4: Data Migration (1 week)
- [ ] Write V5 migration script (risk_story → risk_item)
- [ ] Test migration on staging with production-like data
- [ ] Run validation queries
- [ ] Verify row counts match
- [ ] Verify rich content preserved (hypothesis/condition/consequence)
- [ ] Verify domain risks created correctly
- [ ] Test rollback script
- [ ] Execute production migration
- [ ] Monitor for errors

**Deliverable:** All risk_story data migrated to risk_item

---

### Sprint 5: Code Cleanup (1 week)
- [ ] Remove dead code from RiskAutoCreationServiceImpl
- [ ] Verify build succeeds
- [ ] Run full test suite
- [ ] Update CLAUDE.md documentation
- [ ] Update RISK_AGGREGATION_API.md
- [ ] Create RISK_API_MIGRATION_GUIDE.md

**Deliverable:** Dead code removed, docs updated

---

### Sprint 6: API Deprecation (1 week)
- [ ] Add @Deprecated annotations to RiskStoryController
- [ ] Replace endpoint implementations with 410 Gone responses
- [ ] Add migration guide links in error responses
- [ ] Update OpenAPI/Swagger docs
- [ ] Monitor API usage logs
- [ ] Notify known API consumers

**Deliverable:** Old API returns helpful errors

---

### Sprint 7-8: Monitoring Period (2 weeks)
- [ ] Monitor deprecated endpoint usage (are they still called?)
- [ ] Identify remaining consumers
- [ ] Provide migration support
- [ ] Fix any integration issues
- [ ] Verify zero critical usage

**Deliverable:** Zero active consumers of old API

---

### Sprint 9: Delete Code (1 week)
- [ ] Delete RiskStoryService + Impl
- [ ] Delete RiskStoryRepository
- [ ] Delete RiskStory model classes
- [ ] Delete RiskStory DTOs
- [ ] Delete RiskStoryController
- [ ] Verify build succeeds
- [ ] Run full test suite
- [ ] Deploy to production

**Deliverable:** Zero RiskStory code in codebase

---

### Sprint 10: Database Cleanup (1 week)
- [ ] Archive risk_story tables to archive schema
- [ ] Write V6 migration (drop tables)
- [ ] Test on staging
- [ ] Deploy to production
- [ ] Monitor for errors
- [ ] Schedule 90-day archive deletion

**Deliverable:** risk_story tables removed from public schema

---

**Total Duration:** 10-12 weeks

---

## Risk Mitigation

### Risk 1: Data Loss During Migration
**Mitigation:**
- Archive tables before dropping
- Test on staging with prod-like data
- Validate row counts match
- Keep mapping table for reference
- 90-day retention before final deletion

### Risk 2: Rich Content Not Preserved
**Mitigation:**
- Add hypothesis/condition/consequence columns FIRST (Phase 1)
- Test data migration on sample records
- Run validation queries comparing old vs new
- Manual spot-check of critical risks

### Risk 3: API Breaking Changes
**Mitigation:**
- 2-release deprecation period
- Return 410 Gone with migration guide
- Monitor usage logs
- Provide direct support for consumers
- Keep rollback option available

### Risk 4: Profile Integration Bugs
**Mitigation:**
- Update early (Phase 3, before data migration)
- Add integration tests
- Test graph views thoroughly
- Gradual rollout with monitoring

### Risk 5: Performance Degradation
**Mitigation:**
- Add search index (full-text on rich content)
- Profile queries before/after
- Test with realistic data volumes
- Monitor API response times

---

## Success Criteria

### Phase Completion
- ✅ risk_item has hypothesis/condition/consequence columns
- ✅ Manual risk creation works via POST /api/v1/risk-items
- ✅ Full search available via GET /api/v1/risk-items/search
- ✅ ProfileService queries risk_item (not risk_story)
- ✅ All risk_story data migrated (row counts match ±1%)
- ✅ Rich content preserved (spot-check 100 records)
- ✅ Zero RiskStory references in code
- ✅ risk_story tables dropped (archived for 90 days)

### Data Integrity
- ✅ Row count: risk_story ≈ risk_item (manual created)
- ✅ Hypothesis/condition/consequence fields populated
- ✅ Domain risks have correct item counts
- ✅ Priority scores recalculated correctly
- ✅ Status mappings correct (PENDING_SME_REVIEW → OPEN)
- ✅ ARB assignments match domains

### API Compatibility
- ✅ PO workflows work via /api/v1/risk-items
- ✅ ARB workflows work via /api/v1/domain-risks
- ✅ Profile graph shows risk_item data
- ✅ Search returns expected results
- ✅ Zero calls to deprecated endpoints (monitored 2 weeks)

### Performance
- ✅ Profile graph query time ≤ baseline
- ✅ Risk search query time < 2s (1000+ risks)
- ✅ Full-text search works on rich content
- ✅ Domain aggregation recalc < 100ms

---

## Testing Strategy

### Unit Tests
- [ ] RiskItemService.createManualRiskItem() - Create with rich content
- [ ] RiskItemRepository.searchRiskItems() - Full search with filters
- [ ] ProfileServiceImpl - Updated risk queries
- [ ] Rich content validation (null handling)

### Integration Tests
- [ ] POST /api/v1/risk-items - Create manual risk with hypothesis/condition/consequence
- [ ] GET /api/v1/risk-items/search - Search by domain, priority, status
- [ ] GET /api/apps/{appId}/profile - Verify risk_item data in graph
- [ ] Update risk item - Modify rich content fields

### Migration Tests (Staging)
1. Create 100 test risk_stories with rich content
2. Run V5 migration
3. Validate:
   - Row counts match
   - hypothesis/condition/consequence preserved
   - Domain risks created
   - Priority scores calculated
   - Status mappings correct

### Data Validation Queries
```sql
-- Compare rich content preservation
SELECT
    COUNT(*) as total,
    COUNT(hypothesis) as with_hypothesis,
    COUNT(condition) as with_condition,
    COUNT(consequence) as with_consequence
FROM risk_item WHERE creation_type = 'MANUAL_SME_INITIATED';

-- Spot check sample records
SELECT
    rs.risk_id as old_id,
    ri.risk_item_id as new_id,
    rs.hypothesis = ri.hypothesis as hypothesis_match,
    rs.condition = ri.condition as condition_match,
    rs.consequence = ri.consequence as consequence_match
FROM risk_story rs
JOIN risk_story_to_item_mapping m ON rs.risk_id = m.old_risk_id
JOIN risk_item ri ON m.new_risk_item_id = ri.risk_item_id
LIMIT 20;
```

---

## Documentation Updates

### API Documentation
- [ ] Update `RISK_AGGREGATION_API.md` with manual creation endpoint
- [ ] Add deprecation notices for old endpoints
- [ ] Create `RISK_API_MIGRATION_GUIDE.md`
- [ ] Update OpenAPI/Swagger with rich content fields

### Code Documentation
- [ ] Update `CLAUDE.md` - Remove RiskStory from architecture
- [ ] Update package structure description
- [ ] Document rich content fields in RiskItem

### Testing Documentation
- [ ] Update `TESTING_GUIDE.md` with manual creation scenario
- [ ] Add data migration validation section

---

## Open Questions - RESOLVED

### 1. Rich Content Fields ✅ RESOLVED
**Decision:** Add `hypothesis`, `condition`, `consequence`, `control_refs` columns to risk_item

**Rationale:** User requirement - preserve rich risk narrative structure

---

### 2. Manual Risk Creation ✅ RESOLVED
**Decision:** Yes, add POST /api/v1/risk-items endpoint

**Rationale:** Maintain feature parity with RiskStory

---

### 3. Evidence Attachment Model ✅ RESOLVED
**Decision:** Keep field-level linking (evidence_field_link)

**Rationale:** Evidence already attached to fields; simpler model; risk_story_evidence can be dropped

---

### 4. Status Enum Migration ✅ RESOLVED
**Mapping:**
```
PENDING_SME_REVIEW, AWAITING_EVIDENCE, UNDER_REVIEW → OPEN
APPROVED → RESOLVED
REJECTED, WAIVED → WAIVED
CLOSED → CLOSED
```

**Accept:** Simplified 5-state model

---

## Next Steps

**Immediate Actions:**
1. **Review & Approve Plan** ✅ Awaiting approval
2. **Start Phase 1** - Add columns to risk_item (safe, reversible)
3. **Assign Sprints** - When to start implementation?

**First Code Change:**
Phase 1.1 - Create V4 migration to add rich content columns (zero risk, fully reversible)
