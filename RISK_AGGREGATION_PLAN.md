# Risk Aggregation & Routing Implementation Plan

**Date:** 2025-10-12
**Status:** 📋 Planning Phase - Awaiting Approval

---

## Executive Summary

**Problem:** Current risk management creates one risk per evidence item, overwhelming SMEs with too many individual risks to monitor.

**Solution:** Aggregate risks by control domain per application, with automatic ARB routing and intelligent prioritization.

---

## Current State Analysis

### Current Risk Model
```
Application (1) ──> Risk (N) ──> Evidence (1)
                    │
                    ├─ field_key
                    ├─ triggering_evidence_id
                    ├─ severity
                    └─ assigned_sme
```

**Key Files:**
- `RiskStory` model: `/src/main/java/com/example/gateway/risk/model/RiskStory.java` (88 lines)
- `RiskAutoCreationServiceImpl`: Creates one risk per evidence evaluation (188 lines)
- `profile-fields.registry.yaml`: Contains field rules with `requires_review` flag
- Database: `risk_story` table with 1:1 relationship to evidence

**Current Flow:**
1. Evidence attached to profile field
2. `RiskAutoCreationService.evaluateAndCreateRisk()` called
3. Checks `requires_review` flag for field + app criticality
4. Creates individual `RiskStory` if `requires_review = true`
5. Assigns SME based on field key pattern matching
6. Status: `PENDING_SME_REVIEW`

**Pain Points:**
- ✗ One risk per evidence = 100s of risks for high-criticality apps
- ✗ No domain-level aggregation
- ✗ No ARB routing mechanism
- ✗ Simple pattern-based SME assignment
- ✗ No priority-based risk sorting
- ✗ SMEs see flat list of all risks

---

## Proposed Architecture

### New Risk Model
```
Application (1) ──> DomainRisk (N) ──> RiskItem (N) ──> Evidence (1)
                    │                   │
                    ├─ domain           ├─ field_key
                    ├─ arb              ├─ evidence_id
                    ├─ priority         ├─ status
                    ├─ severity         ├─ severity
                    └─ risk_items[]     └─ priority_score
```

**Domain-Level Aggregation:**
- One `DomainRisk` per domain per app (e.g., "Security Domain Risk for App X")
- Contains multiple `RiskItem` entries (what are currently individual risks)
- Automatically routes to appropriate ARB based on domain

**Views:**
- **PO View:** Evidence-level detail (current `RiskItem` view)
- **SME View:** Domain-level aggregation with drill-down capability

---

## Implementation Plan

### Phase 1: Database Schema Changes

#### 1.1 Create `domain_risk` Table
**File:** New migration script
```sql
CREATE TABLE domain_risk (
    domain_risk_id VARCHAR(255) PRIMARY KEY,
    app_id VARCHAR(255) NOT NULL,
    domain VARCHAR(100) NOT NULL,           -- security, integrity, availability, etc.
    derived_from VARCHAR(100) NOT NULL,     -- security_rating, integrity_rating, etc.
    arb VARCHAR(100) NOT NULL,              -- security_arb, integrity_arb, etc.

    -- Aggregated metadata
    title VARCHAR(500),
    description TEXT,
    total_items INTEGER DEFAULT 0,
    open_items INTEGER DEFAULT 0,
    high_priority_items INTEGER DEFAULT 0,

    -- Priority & severity (calculated from items)
    overall_priority VARCHAR(50),           -- CRITICAL, HIGH, MEDIUM, LOW
    overall_severity VARCHAR(50),           -- high, medium, low
    priority_score INTEGER,                 -- Calculated: 0-100

    -- Status
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING_ARB_REVIEW',

    -- Assignment
    assigned_arb VARCHAR(100),
    assigned_at TIMESTAMP,

    -- Lifecycle
    opened_at TIMESTAMP NOT NULL,
    closed_at TIMESTAMP,
    last_item_added_at TIMESTAMP,

    -- Audit
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- Unique constraint: one domain risk per domain per app
    CONSTRAINT uk_app_domain UNIQUE (app_id, domain)
);

CREATE INDEX idx_domain_risk_app ON domain_risk(app_id);
CREATE INDEX idx_domain_risk_arb ON domain_risk(assigned_arb);
CREATE INDEX idx_domain_risk_status ON domain_risk(status);
CREATE INDEX idx_domain_risk_priority ON domain_risk(priority_score DESC);
```

#### 1.2 Create `risk_item` Table (Rename/Restructure `risk_story`)
**File:** New migration script
```sql
CREATE TABLE risk_item (
    risk_item_id VARCHAR(255) PRIMARY KEY,
    domain_risk_id VARCHAR(255) NOT NULL,

    -- References
    app_id VARCHAR(255) NOT NULL,
    field_key VARCHAR(255) NOT NULL,
    profile_field_id VARCHAR(255),
    triggering_evidence_id VARCHAR(255),
    track_id VARCHAR(255),

    -- Content (simplified from RiskStory)
    title VARCHAR(500),
    description TEXT,

    -- Priority & severity
    priority VARCHAR(50),                   -- From registry: CRITICAL, HIGH, MEDIUM, LOW
    severity VARCHAR(50),                   -- From evidence status
    priority_score INTEGER,                 -- Calculated score
    evidence_status VARCHAR(50),            -- missing, expiring, expired, rejected

    -- Status
    status VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    resolution VARCHAR(50),
    resolution_comment TEXT,

    -- Lifecycle
    creation_type VARCHAR(50),
    raised_by VARCHAR(255),
    opened_at TIMESTAMP NOT NULL,
    resolved_at TIMESTAMP,

    -- Snapshot
    policy_requirement_snapshot JSONB,

    -- Audit
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (domain_risk_id) REFERENCES domain_risk(domain_risk_id) ON DELETE CASCADE
);

CREATE INDEX idx_risk_item_domain_risk ON risk_item(domain_risk_id);
CREATE INDEX idx_risk_item_app ON risk_item(app_id);
CREATE INDEX idx_risk_item_field ON risk_item(field_key);
CREATE INDEX idx_risk_item_evidence ON risk_item(triggering_evidence_id);
CREATE INDEX idx_risk_item_priority ON risk_item(priority_score DESC);
```

#### 1.3 Migration Strategy for Existing `risk_story` Data
**Approach:** Dual-write pattern during transition
1. Keep `risk_story` table (deprecated, read-only)
2. Write to new `domain_risk` + `risk_item` tables
3. Background job to migrate existing risks
4. Remove `risk_story` table after migration complete

---

### Phase 2: Registry Schema Updates

#### 2.1 Update `profile-fields.registry.yaml` Structure
**File:** `src/main/resources/profile-fields.registry.yaml`

**Add two new fields per rule:**
```yaml
fields:
  - key: encryption_at_rest
    label: Encryption at Rest
    derived_from: security_rating
    arb: security_arb              # NEW: ARB routing
    rule:
      A1:
        value: required
        label: "Required"
        ttl: 90d
        requires_review: true
        priority: CRITICAL           # NEW: Priority level
      A2:
        value: required
        label: "Required"
        ttl: 90d
        requires_review: true
        priority: HIGH               # NEW: Priority level
      B:
        value: required
        label: "Required"
        ttl: 180d
        requires_review: true
        priority: MEDIUM             # NEW: Priority level
      C:
        value: optional
        label: "Optional"
        ttl: 365d
        requires_review: false
        priority: LOW                # NEW: Priority level
```

**ARB Mapping (to be added globally):**
```yaml
# Top-level ARB configuration
arb_config:
  security_rating: security_arb
  integrity_rating: integrity_arb
  availability_rating: availability_arb
  resilience_rating: resilience_arb
  confidentiality_rating: confidentiality_arb
  app_criticality_assessment: governance_arb
```

**Priority Levels:** CRITICAL, HIGH, MEDIUM, LOW

---

### Phase 3: Domain Model Changes

#### 3.1 New Entity: `DomainRisk`
**File:** `src/main/java/com/example/gateway/risk/model/DomainRisk.java` (NEW)
```java
@Data
@Entity
@Table(name = "domain_risk")
public class DomainRisk {
    @Id
    private String domainRiskId;

    @Column(nullable = false)
    private String appId;

    @Column(nullable = false)
    private String domain;

    @Column(nullable = false)
    private String derivedFrom;

    @Column(nullable = false)
    private String arb;

    // Aggregated metadata
    private String title;
    private String description;
    private Integer totalItems = 0;
    private Integer openItems = 0;
    private Integer highPriorityItems = 0;

    // Calculated priority/severity
    private String overallPriority;
    private String overallSeverity;
    private Integer priorityScore;

    @Enumerated(EnumType.STRING)
    private DomainRiskStatus status = DomainRiskStatus.PENDING_ARB_REVIEW;

    private String assignedArb;
    private OffsetDateTime assignedAt;

    private OffsetDateTime openedAt;
    private OffsetDateTime closedAt;
    private OffsetDateTime lastItemAddedAt;

    // Relationships
    @OneToMany(mappedBy = "domainRisk", cascade = CascadeType.ALL)
    private List<RiskItem> riskItems = new ArrayList<>();

    // Audit
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
```

#### 3.2 New Entity: `RiskItem`
**File:** `src/main/java/com/example/gateway/risk/model/RiskItem.java` (NEW)
```java
@Data
@Entity
@Table(name = "risk_item")
public class RiskItem {
    @Id
    private String riskItemId;

    @Column(nullable = false)
    private String domainRiskId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "domain_risk_id", insertable = false, updatable = false)
    private DomainRisk domainRisk;

    @Column(nullable = false)
    private String appId;

    @Column(nullable = false)
    private String fieldKey;

    private String profileFieldId;
    private String triggeringEvidenceId;
    private String trackId;

    private String title;
    private String description;

    @Enumerated(EnumType.STRING)
    private RiskPriority priority;

    @Enumerated(EnumType.STRING)
    private RiskSeverity severity;

    private Integer priorityScore;
    private String evidenceStatus;

    @Enumerated(EnumType.STRING)
    private RiskItemStatus status = RiskItemStatus.OPEN;

    private String resolution;
    private String resolutionComment;

    @Enumerated(EnumType.STRING)
    private RiskCreationType creationType;

    private String raisedBy;
    private OffsetDateTime openedAt;
    private OffsetDateTime resolvedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> policyRequirementSnapshot;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
```

#### 3.3 New Enums
**File:** `src/main/java/com/example/gateway/risk/model/RiskPriority.java` (NEW)
```java
public enum RiskPriority {
    CRITICAL,  // Score: 90-100
    HIGH,      // Score: 70-89
    MEDIUM,    // Score: 40-69
    LOW        // Score: 0-39
}
```

**File:** `src/main/java/com/example/gateway/risk/model/DomainRiskStatus.java` (NEW)
```java
public enum DomainRiskStatus {
    PENDING_ARB_REVIEW,   // Awaiting ARB assignment/review
    UNDER_ARB_REVIEW,     // ARB is reviewing
    AWAITING_REMEDIATION, // Items need PO action
    IN_PROGRESS,          // Remediation in progress
    RESOLVED,             // All items resolved
    WAIVED,               // ARB waived the domain risk
    CLOSED                // Administratively closed
}
```

**File:** `src/main/java/com/example/gateway/risk/model/RiskItemStatus.java` (NEW)
```java
public enum RiskItemStatus {
    OPEN,              // Needs attention
    IN_PROGRESS,       // Being worked on
    RESOLVED,          // Evidence provided/accepted
    WAIVED,            // ARB waived this item
    CLOSED             // Closed without action
}
```

---

### Phase 4: Service Layer Changes

#### 4.1 Create `DomainRiskAggregationService`
**File:** `src/main/java/com/example/gateway/risk/service/DomainRiskAggregationService.java` (NEW)

**Responsibilities:**
- Create or update domain-level risks
- Add risk items to domain risks
- Calculate priority scores
- Update aggregated metrics

**Key Methods:**
```java
public interface DomainRiskAggregationService {
    // Create or get existing domain risk for app+domain
    DomainRisk getOrCreateDomainRisk(String appId, String domain, String derivedFrom, String arb);

    // Add risk item to domain risk
    RiskItem addRiskItem(String domainRiskId, RiskItemCreateRequest request);

    // Calculate priority score based on multiple factors
    int calculatePriorityScore(RiskPriority priority, String evidenceStatus,
                               String appCriticality, String fieldKey);

    // Update domain risk aggregated metrics
    void updateDomainRiskMetrics(String domainRiskId);

    // Assign ARB based on domain
    void assignArb(String domainRiskId);
}
```

#### 4.2 Create `RiskPriorityCalculator`
**File:** `src/main/java/com/example/gateway/risk/service/RiskPriorityCalculator.java` (NEW)

**Priority Score Formula:**
```java
Priority Score =
    Base Priority (40%) +         // From registry: CRITICAL=40, HIGH=30, MEDIUM=20, LOW=10
    App Criticality (30%) +       // A=30, B=20, C=10, D=5
    Evidence Status (20%) +       // missing=20, expired=15, expiring=10, rejected=5
    Field Sensitivity (10%)       // Based on compliance framework mappings

Example:
- CRITICAL priority + A rating + missing evidence + SOC2 field = 40 + 30 + 20 + 10 = 100
- LOW priority + D rating + valid evidence + Internal field = 10 + 5 + 0 + 5 = 20
```

**Key Methods:**
```java
public interface RiskPriorityCalculator {
    int calculateScore(RiskPriority priority, String evidenceStatus,
                      String appCriticality, String fieldKey);

    RiskPriority determinePriorityLevel(int score);

    String determineEvidenceStatus(String evidenceId);
}
```

#### 4.3 Update `RiskAutoCreationService`
**File:** `src/main/java/com/example/gateway/risk/service/RiskAutoCreationServiceImpl.java`

**Changes:**
1. Instead of creating `RiskStory`, create `RiskItem` within `DomainRisk`
2. Look up ARB from registry
3. Calculate priority score
4. Add to existing domain risk or create new one

**Updated Flow:**
```java
@Override
@Transactional
public AutoRiskCreationResponse evaluateAndCreateRiskItem(
        String evidenceId, String profileFieldId, String appId) {

    // 1. Get field info
    String fieldKey = getFieldKeyFromProfileFieldId(profileFieldId);
    String appRating = getAppRatingForField(appId, fieldKey);

    // 2. Get registry config (NOW includes priority + arb)
    FieldRiskConfig config = registryService.getFieldRiskConfig(fieldKey);
    RiskCreationRule rule = config.getRuleForCriticality(appRating);

    if (!rule.requiresReview()) {
        return AutoRiskCreationResponse.notCreated(...);
    }

    // 3. Determine domain and ARB from registry
    String derivedFrom = config.derivedFrom();
    String domain = mapDerivedFromToDomain(derivedFrom);
    String arb = registryService.getArbForDerivedFrom(derivedFrom);

    // 4. Get or create domain risk
    DomainRisk domainRisk = domainRiskAggregationService
        .getOrCreateDomainRisk(appId, domain, derivedFrom, arb);

    // 5. Calculate priority score
    String evidenceStatus = determineEvidenceStatus(evidenceId);
    RiskPriority priority = rule.priority();  // From registry
    int priorityScore = riskPriorityCalculator.calculateScore(
        priority, evidenceStatus, appRating, fieldKey);

    // 6. Create risk item
    RiskItem riskItem = domainRiskAggregationService.addRiskItem(
        domainRisk.getDomainRiskId(),
        RiskItemCreateRequest.builder()
            .appId(appId)
            .fieldKey(fieldKey)
            .triggeringEvidenceId(evidenceId)
            .priority(priority)
            .priorityScore(priorityScore)
            .evidenceStatus(evidenceStatus)
            .build()
    );

    // 7. Update domain risk metrics
    domainRiskAggregationService.updateDomainRiskMetrics(domainRisk.getDomainRiskId());

    return AutoRiskCreationResponse.created(...);
}
```

#### 4.4 Create `ArbRoutingService`
**File:** `src/main/java/com/example/gateway/risk/service/ArbRoutingService.java` (NEW)

**Responsibilities:**
- Route domain risks to appropriate ARB
- Load balancing across ARB members
- Notification to ARB members

---

### Phase 5: Repository Layer Changes

#### 5.1 Create `DomainRiskRepository`
**File:** `src/main/java/com/example/gateway/risk/repository/DomainRiskRepository.java` (NEW)

```java
public interface DomainRiskRepository extends JpaRepository<DomainRisk, String> {

    Optional<DomainRisk> findByAppIdAndDomain(String appId, String domain);

    List<DomainRisk> findByAssignedArb(String arb);

    List<DomainRisk> findByAssignedArbAndStatus(String arb, DomainRiskStatus status);

    @Query("""
        SELECT dr FROM DomainRisk dr
        WHERE dr.assignedArb = :arb
        AND dr.status IN :statuses
        ORDER BY dr.priorityScore DESC, dr.openItems DESC
        """)
    List<DomainRisk> findByArbPrioritized(
        @Param("arb") String arb,
        @Param("statuses") List<DomainRiskStatus> statuses);

    // For SME view: domain-level aggregation
    @Query("""
        SELECT dr.domain, COUNT(dr), SUM(dr.openItems), AVG(dr.priorityScore)
        FROM DomainRisk dr
        WHERE dr.assignedArb = :arb
        AND dr.status IN :statuses
        GROUP BY dr.domain
        ORDER BY AVG(dr.priorityScore) DESC
        """)
    List<Object[]> getDomainSummaryForArb(
        @Param("arb") String arb,
        @Param("statuses") List<DomainRiskStatus> statuses);
}
```

#### 5.2 Create `RiskItemRepository`
**File:** `src/main/java/com/example/gateway/risk/repository/RiskItemRepository.java` (NEW)

```java
public interface RiskItemRepository extends JpaRepository<RiskItem, String> {

    List<RiskItem> findByDomainRiskId(String domainRiskId);

    List<RiskItem> findByAppId(String appId);

    // For PO view: evidence-level detail
    List<RiskItem> findByAppIdOrderByPriorityScoreDesc(String appId);

    @Query("""
        SELECT ri FROM RiskItem ri
        WHERE ri.appId = :appId
        AND ri.status = :status
        ORDER BY ri.priorityScore DESC, ri.openedAt ASC
        """)
    List<RiskItem> findByAppIdAndStatusPrioritized(
        @Param("appId") String appId,
        @Param("status") RiskItemStatus status);

    boolean existsByAppIdAndFieldKeyAndTriggeringEvidenceId(
        String appId, String fieldKey, String triggeringEvidenceId);
}
```

---

### Phase 6: API/Controller Changes

#### 6.1 Create `DomainRiskController`
**File:** `src/main/java/com/example/gateway/risk/controller/DomainRiskController.java` (NEW)

**SME View Endpoints:**
```java
@RestController
@RequestMapping("/api/domain-risks")
public class DomainRiskController {

    // List domain risks for an ARB (SME view)
    @GetMapping
    public List<DomainRiskSummaryResponse> listForArb(
        @RequestParam String arb,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "priority") String sortBy
    );

    // Get single domain risk with all items (drill-down)
    @GetMapping("/{domainRiskId}")
    public DomainRiskDetailResponse getDetail(
        @PathVariable String domainRiskId
    );

    // Get domain risk items (evidence-level detail)
    @GetMapping("/{domainRiskId}/items")
    public List<RiskItemResponse> getItems(
        @PathVariable String domainRiskId,
        @RequestParam(required = false) String status
    );

    // ARB actions
    @PostMapping("/{domainRiskId}/review")
    public DomainRiskResponse review(
        @PathVariable String domainRiskId,
        @RequestBody ArbReviewRequest request
    );

    @PostMapping("/{domainRiskId}/waive")
    public DomainRiskResponse waive(
        @PathVariable String domainRiskId,
        @RequestBody ArbWaiveRequest request
    );
}
```

#### 6.2 Update `RiskStoryController` → `RiskItemController`
**File:** Rename to `src/main/java/com/example/gateway/risk/controller/RiskItemController.java`

**PO View Endpoints:**
```java
@RestController
@RequestMapping("/api/apps/{appId}/risk-items")
public class RiskItemController {

    // List risk items for an app (PO view)
    @GetMapping
    public PageResponse<RiskItemResponse> listForApp(
        @PathVariable String appId,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int pageSize
    );

    // Get single risk item detail
    @GetMapping("/{riskItemId}")
    public RiskItemResponse getDetail(@PathVariable String riskItemId);

    // PO can resolve individual items
    @PostMapping("/{riskItemId}/resolve")
    public RiskItemResponse resolve(
        @PathVariable String riskItemId,
        @RequestBody ResolveRiskItemRequest request
    );
}
```

---

### Phase 7: Registry Service Updates

#### 7.1 Update `ProfileFieldRegistryService`
**File:** `src/main/java/com/example/gateway/profile/service/ProfileFieldRegistryService.java`

**Add methods:**
```java
// Get ARB for a derived_from value
String getArbForDerivedFrom(String derivedFrom);

// Get priority for a field at specific criticality
RiskPriority getPriorityForField(String fieldKey, String criticality);

// Get all ARB configurations
Map<String, String> getArbMappings();
```

#### 7.2 Update `RegistryRiskConfigService`
**File:** `src/main/java/com/example/gateway/registry/service/RegistryRiskConfigService.java`

**Update `RegistryRuleEvaluation` to include:**
- `priority` (from registry)
- `arb` (from registry)

---

## Implementation Timeline

### Sprint 1 (Week 1-2): Database & Domain Models
- ✅ Create database migration scripts
- ✅ Create `DomainRisk` and `RiskItem` entities
- ✅ Create new enums
- ✅ Create repositories with basic queries
- ✅ Write unit tests for entities

### Sprint 2 (Week 3): Registry Updates
- ✅ Update `profile-fields.registry.yaml` with `arb` and `priority` fields
- ✅ Update `FieldRiskConfig` and `RiskCreationRule` DTOs
- ✅ Update `ProfileFieldRegistryService` to parse new fields
- ✅ Write tests for registry parsing

### Sprint 3 (Week 4): Core Service Logic
- ✅ Create `RiskPriorityCalculator` with scoring logic
- ✅ Create `DomainRiskAggregationService`
- ✅ Create `ArbRoutingService`
- ✅ Update `RiskAutoCreationService` to use new flow
- ✅ Write comprehensive service tests

### Sprint 4 (Week 5): API Layer
- ✅ Create `DomainRiskController` (SME view)
- ✅ Update/rename `RiskItemController` (PO view)
- ✅ Create DTOs for requests/responses
- ✅ Write integration tests for controllers

### Sprint 5 (Week 6): Migration & Testing
- ✅ Write data migration script for existing `risk_story` → `risk_item`
- ✅ Create background job for ongoing migration
- ✅ End-to-end testing with real data
- ✅ Performance testing

### Sprint 6 (Week 7): Documentation & Rollout
- ✅ Update API documentation
- ✅ Update CLAUDE.md with new architecture
- ✅ Create migration guide
- ✅ Gradual rollout with feature flag

---

## Testing Strategy

### Unit Tests
- Priority score calculation logic
- Domain risk aggregation
- ARB routing logic
- Registry parsing with new fields

### Integration Tests
- Full flow: evidence → risk item → domain risk
- Domain risk metrics update
- ARB assignment
- Priority-based sorting

### Performance Tests
- Handle 1000+ risk items per domain risk
- Query performance for SME view (domain aggregation)
- Query performance for PO view (filtered by app)

---

## Rollout Strategy

### Phase A: Dual Write
1. Deploy code with feature flag `features.domainRiskAggregation=false`
2. Start dual-write: write to both old and new tables
3. Monitor for data consistency issues

### Phase B: Gradual Read Migration
1. Enable read from new tables for 10% of apps
2. Compare results, fix discrepancies
3. Gradually increase to 100%

### Phase C: Full Cutover
1. Enable `features.domainRiskAggregation=true`
2. Stop writing to old `risk_story` table
3. Mark old table for deprecation

### Phase D: Cleanup
1. Archive old `risk_story` data
2. Drop deprecated table after 90 days

---

## Key Decisions & Trade-offs

### Decision 1: Keep RiskItem Separate from Evidence
**Rationale:** Allows multiple risk items to reference same evidence, and risk items can exist even if evidence is deleted.

### Decision 2: Calculate Priority Score vs. Static Priority
**Rationale:** Dynamic scoring based on multiple factors provides better prioritization than static registry priority alone.

### Decision 3: One Domain Risk per Domain per App
**Rationale:** Balances aggregation (reduces noise) with granularity (can still drill down). Alternative of one risk per app would lose domain context.

### Decision 4: ARB at Domain Level, not App Level
**Rationale:** Security ARB shouldn't review integrity risks. Domain-specific ARBs have appropriate expertise.

---

## Risks & Mitigations

### Risk 1: Performance with Large Domain Risks
**Concern:** Domain risks with 1000+ items may be slow to load
**Mitigation:**
- Paginate risk items in API
- Add database indexes on `domain_risk_id` + `priority_score`
- Cache domain risk summary metrics

### Risk 2: Data Migration Complexity
**Concern:** Migrating existing risks without losing context
**Mitigation:**
- Keep old table as read-only reference
- Dual-write during transition
- Thorough testing before cutover

### Risk 3: ARB Assignment Logic
**Concern:** May need more sophisticated assignment than simple mapping
**Mitigation:**
- Start with simple derived_from → arb mapping
- Build in extension points for future enhancement
- Allow manual ARB reassignment

---

## Open Questions

1. **ARB Member Management:** How do we manage ARB membership? (Future: ARB team table?)
2. **Notifications:** Should ARB members be notified when domain risks are assigned?
3. **SLAs:** Are there time-based SLAs for ARB reviews?
4. **Escalation:** What happens if ARB doesn't review within SLA?
5. **Historical Tracking:** Should we maintain history of priority score changes?

---

## Success Metrics

### Quantitative
- ✅ Reduce SME workload by 80% (100 individual risks → 5-10 domain risks)
- ✅ Improve response time: 95th percentile < 500ms for SME view
- ✅ Enable prioritization: High-priority items reviewed 3x faster

### Qualitative
- ✅ SMEs report clearer overview of risks across portfolio
- ✅ POs still have detailed evidence-level visibility
- ✅ ARB members can efficiently focus on high-priority domains

---

**Status:** 📋 Ready for Review
**Next Step:** Stakeholder approval before implementation
**Owner:** Development Team
**Reviewers:** Security SMEs, Architecture Team, Product Owners
