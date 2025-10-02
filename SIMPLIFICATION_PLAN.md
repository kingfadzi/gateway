# Codebase Simplification Plan

**Date:** 2025-10-01
**Status:** Planning Phase
**Goal:** Reduce complexity hotspots and improve maintainability

---

## 🔥 TOP COMPLEXITY HOTSPOTS

### **1. EvidenceRepository - 1,283 lines** ⚠️ CRITICAL
**Location:** `src/main/java/com/example/gateway/evidence/repository/EvidenceRepository.java`

**Problem:** God object with 28 public methods doing everything
- CRUD operations
- Complex search queries
- 4 KPI state queries (compliant, pending, missing, risk-blocked)
- Workbench evidence queries
- Document attachment queries
- Massive code duplication (each query has a near-identical count method)

**Simplification Strategy:** Split into 4 focused repositories
```
EvidenceRepository (core CRUD - ~300 lines)
EvidenceSearchRepository (search/filter - ~400 lines)
EvidenceKpiRepository (KPI state queries - ~300 lines)
EvidenceDocumentRepository (document operations - ~200 lines)
```

**Impact:** Reduces largest file from 1,283 → ~300 lines each
**Effort:** 1-2 weeks
**Risk:** Low (repositories are well isolated)

---

### **2. EvidenceServiceImpl - 891 lines** ⚠️ HIGH
**Location:** `src/main/java/com/example/gateway/evidence/service/EvidenceServiceImpl.java`

**Problem:** Service doing too much
- 20+ public methods
- Complex orchestration mixing multiple domains
- Methods like `createEvidenceWithDocument()` are 97 lines long
- Violates Single Responsibility Principle

**Simplification Strategy:** Extract orchestration layer
```
EvidenceService (core evidence operations - ~400 lines)
EvidenceOrchestrationService (workflows - ~300 lines)
EvidenceAttestationService (already exists - keep!)
```

**Impact:** 891 → ~400 lines core service + ~300 orchestration
**Effort:** 1 week
**Risk:** Medium (need careful transaction management)

---

### **3. ProfileServiceImpl - 492 lines** ⚠️ MEDIUM
**Location:** `src/main/java/com/example/gateway/profile/service/ProfileServiceImpl.java`

**Problem:**
- N+1 query problem (fetches evidence for each field in a loop)
- Complex graph building logic
- Mix of concerns (profile CRUD + graph building + KPI calculation)

**Simplification Strategy:**
1. Extract `ProfileGraphBuilder` for graph construction
2. Batch load evidence to eliminate N+1
3. Extract profile versioning logic

**Current N+1 Problem:**
```java
// BAD: Separate DB call for EACH field
for (ProfileField field : fields) {
    List<Evidence> evidence = evidenceRepo.find(field.id());
}
```

**After Fix:**
```java
// GOOD: Single batch query for ALL fields
Map<String, List<Evidence>> evidenceByField =
    evidenceRepo.findByFieldIds(allFieldIds);
```

**Impact:** 50x performance improvement for 50-field profiles
**Effort:** 3-4 days
**Risk:** Low

---

### **4. DocumentRepository - 484 lines** ⚠️ MEDIUM
**Location:** `src/main/java/com/example/gateway/document/repository/DocumentRepository.java`

**Problem:**
- Multiple query variants with similar logic
- Field-based queries duplicated across methods

**Simplification Strategy:** Extract common SQL building utilities

**Impact:** 484 → ~350 lines (-28%)
**Effort:** 3-5 days
**Risk:** Low

---

### **5. RiskStoryServiceImpl - 411 lines** ⚠️ LOW
**Location:** `src/main/java/com/example/gateway/risk/service/RiskStoryServiceImpl.java`

**Problem:**
- Risk creation orchestration
- Evidence attachment logic
- SME assignment logic all mixed

**Simplification Strategy:** Already partially split (RiskAutoCreationService exists), continue the pattern

**Impact:** 411 → ~300 lines (-27%)
**Effort:** 3-5 days
**Risk:** Low

---

## 📊 SIMPLIFICATION METRICS

| File | Current Lines | Methods | After Split | Reduction |
|------|---------------|---------|-------------|-----------|
| EvidenceRepository | 1,283 | 28 | 4 × ~300 | -68% complexity |
| EvidenceServiceImpl | 891 | 20+ | 2 × ~400 | -55% |
| ProfileServiceImpl | 492 | 12 | 2 × ~250 | -49% |
| DocumentRepository | 484 | 15 | ~350 | -28% |
| RiskStoryServiceImpl | 411 | 10 | ~300 | -27% |

**Total Lines to Simplify:** 3,561 lines
**Expected After Simplification:** ~2,100 lines
**Overall Reduction:** -41% in hotspot complexity

---

## 🎯 RECOMMENDED EXECUTION ORDER

### **Phase 1: EvidenceRepository Split (HIGHEST IMPACT)**
**Duration:** 1-2 weeks
**Priority:** P0

Split the 1,283-line monster first - it touches everything.

**Steps:**
1. ✅ Create `EvidenceKpiRepository` interface and implementation
   - Move `findCompliantEvidence()` + `countCompliantEvidence()`
   - Move `findPendingReviewEvidence()` + `countPendingReviewEvidence()`
   - Move `findMissingEvidenceFields()` + `countMissingEvidenceFields()`
   - Move `findRiskBlockedItems()` + `countRiskBlockedItems()`
   - **Lines moved:** ~300

2. ✅ Create `EvidenceSearchRepository` interface and implementation
   - Move `searchEvidence()`
   - Move `searchWorkbenchEvidence()`
   - Move filter/search helper methods
   - **Lines moved:** ~400

3. ✅ Create `EvidenceDocumentRepository` interface and implementation
   - Move `findEnhancedAttachedDocuments()`
   - Move `getDocumentsWithAttachmentStatus()`
   - Move document-related queries
   - **Lines moved:** ~200

4. ✅ Keep `EvidenceRepository` for core CRUD
   - `save()`, `findById()`, `findByAppId()`, etc.
   - **Remaining:** ~300 lines

5. ✅ Update `EvidenceServiceImpl` to use new repositories
   - Inject 4 repositories instead of 1
   - Update method calls to use appropriate repository

**Testing Strategy:**
- Keep all existing tests passing
- Add new tests for each repository in isolation

---

### **Phase 2: Extract SQL Query Builder Utility (HIGH IMPACT)**
**Duration:** 1 week
**Priority:** P1

Create `SqlQueryBuilder` utility to eliminate 70% duplication

**Problem:**
```java
// This pattern is duplicated 8+ times across methods:
StringBuilder sql = new StringBuilder();
if (criticality != null && !criticality.isEmpty()) {
    String[] values = criticality.split(",");
    sql.append(" AND app.criticality IN (");
    for (int i = 0; i < values.length; i++) {
        sql.append(":criticality").append(i);
        if (i < values.length - 1) sql.append(", ");
        params.put("criticality" + i, values[i].trim());
    }
    sql.append(")");
}
```

**Solution:**
```java
public class EvidenceQueryBuilder {

    public void addCriticalityFilter(StringBuilder sql,
                                     MapSqlParameterSource params,
                                     String criticality) {
        if (criticality != null && !criticality.isEmpty()) {
            String[] values = criticality.split(",");
            sql.append(" AND app.criticality IN (");
            for (int i = 0; i < values.length; i++) {
                sql.append(":criticality").append(i);
                if (i < values.length - 1) sql.append(", ");
                params.addValue("criticality" + i, values[i].trim());
            }
            sql.append(")");
        }
    }

    public void addSearchFilter(StringBuilder sql,
                               MapSqlParameterSource params,
                               String searchTerm) {
        // Extract common search logic
    }

    public void addPaginationAndSort(StringBuilder sql,
                                     MapSqlParameterSource params,
                                     String sortBy, String sortOrder,
                                     int limit, int offset) {
        // Extract common pagination logic
    }
}
```

**Impact:**
- Reduces repository methods by 30-40% in size
- Eliminates duplicate filter logic
- Easier to add new filters consistently

**Files to Update:**
- `EvidenceRepository.java`
- `EvidenceKpiRepository.java`
- `EvidenceSearchRepository.java`
- `RiskStoryRepository.java`

---

### **Phase 3: EvidenceServiceImpl Orchestration Extraction**
**Duration:** 1 week
**Priority:** P1

Extract `EvidenceOrchestrationService` for multi-domain workflows

**Methods to Move:**
```java
// Move to EvidenceOrchestrationService:
- createEvidenceWithDocument() - 97 lines
- Complex attestation workflows
- Auto-risk triggering logic
- Multi-step validation flows
```

**Keep in EvidenceService:**
```java
// Core evidence operations:
- createEvidence()
- getEvidenceById()
- getEvidenceByApp()
- updateEvidence()
- deleteEvidence()
```

**Pattern:**
```java
@Service
public class EvidenceOrchestrationService {

    private final EvidenceService evidenceService;
    private final DocumentService documentService;
    private final EvidenceFieldLinkService fieldLinkService;
    private final RiskAutoCreationService riskService;

    @Transactional
    public EvidenceWithDocumentResponse createEvidenceWithDocument(
            String appId, CreateEvidenceWithDocumentRequest request) {

        // Step 1: Create document
        DocumentResponse document = documentService.createDocument(appId, ...);

        // Step 2: Create evidence
        Evidence evidence = evidenceService.createEvidence(appId, ...);

        // Step 3: Link to profile field
        EvidenceFieldLinkResponse link =
            fieldLinkService.attachEvidenceToField(...);

        // Step 4: Trigger auto-risk creation
        riskService.evaluateAndCreateIfNeeded(...);

        return buildResponse(evidence, document, link);
    }
}
```

**Benefits:**
- Clear separation of core vs orchestration logic
- Easier to test services in isolation
- Explicit transaction boundaries
- Service responsibilities are clearer

---

### **Phase 4: Fix ProfileService N+1 Queries**
**Duration:** 3-4 days
**Priority:** P0 (Performance Critical)

Batch load evidence instead of loop queries

**Current Problem (line 222 in ProfileServiceImpl.java):**
```java
for (ProfileField field : domainFields) {
    // ❌ SEPARATE DATABASE CALL FOR EACH FIELD
    List<EnhancedEvidenceSummary> evidence =
        evidenceRepository.findEvidenceByProfileField(field.fieldId(), 100, 0);

    // Process evidence...
}
```

**For 50 fields = 50 database queries!**

**Solution:**
```java
// Step 1: Collect all field IDs
List<String> allFieldIds = domainFields.stream()
    .map(ProfileField::fieldId)
    .collect(Collectors.toList());

// Step 2: Single batch query
Map<String, List<EnhancedEvidenceSummary>> evidenceByField =
    evidenceRepository.findEvidenceByProfileFieldsBatch(allFieldIds)
        .stream()
        .collect(Collectors.groupingBy(EnhancedEvidenceSummary::profileFieldId));

// Step 3: Lookup in memory
for (ProfileField field : domainFields) {
    List<EnhancedEvidenceSummary> evidence =
        evidenceByField.getOrDefault(field.fieldId(), Collections.emptyList());

    // Process evidence...
}
```

**New Repository Method:**
```java
// Add to EvidenceRepository
public List<EnhancedEvidenceSummary> findEvidenceByProfileFieldsBatch(
        List<String> profileFieldIds) {

    String sql = """
        SELECT e.*, pf.*, d.*
        FROM evidence e
        JOIN profile_field pf ON e.profile_field_id = pf.id
        LEFT JOIN document d ON e.document_id = d.id
        WHERE pf.id IN (:fieldIds)
        ORDER BY e.created_at DESC
        """;

    MapSqlParameterSource params = new MapSqlParameterSource();
    params.addValue("fieldIds", profileFieldIds);

    return jdbc.query(sql, params, evidenceMapper);
}
```

**Performance Impact:**
- **Before:** 50 queries @ 10ms each = 500ms
- **After:** 1 query @ 15ms = 15ms
- **Improvement:** 33x faster!

---

## 🔧 QUICK WINS (Can do immediately)

### **1. Extract Common SQL to Utility Class**
**Effort:** 1-2 days
**Impact:** Reduce duplication by 40%

Create `SqlFilterBuilder` class for common patterns:
- Criticality filtering
- Search term filtering
- Date range filtering
- Pagination and sorting

### **2. Consolidate Duplicate Count Methods**
**Effort:** 1 day
**Impact:** Remove 8+ duplicate methods

Pattern:
```java
// Instead of duplicating count logic:
public long countCompliantEvidence(...) {
    // 50 lines of duplicated SQL building
}

// Extract shared logic:
private String buildFilterSql(FilterSpec spec) {
    // Common filter building
}

public List<Evidence> findEvidence(FilterSpec spec, Pageable page) {
    String sql = "SELECT * FROM ..." + buildFilterSql(spec);
    // Add pagination
}

public long countEvidence(FilterSpec spec) {
    String sql = "SELECT COUNT(*) FROM ..." + buildFilterSql(spec);
    // No pagination
}
```

### **3. Move Complex SQL to External Files**
**Effort:** 2-3 days
**Impact:** Better maintainability, easier to optimize

Instead of building SQL in Java:
```
resources/sql/evidence/
  ├── find-compliant.sql
  ├── count-compliant.sql
  ├── find-pending-review.sql
  ├── count-pending-review.sql
  ├── find-missing-evidence.sql
  ├── count-missing-evidence.sql
  ├── find-risk-blocked.sql
  └── count-risk-blocked.sql
```

Load with:
```java
@Repository
public class EvidenceKpiRepository {

    @Value("classpath:sql/evidence/find-compliant.sql")
    private Resource compliantQueryResource;

    private String compliantQuery;

    @PostConstruct
    public void init() throws IOException {
        compliantQuery = StreamUtils.copyToString(
            compliantQueryResource.getInputStream(),
            StandardCharsets.UTF_8);
    }
}
```

**Benefits:**
- SQL syntax highlighting in IDE
- Easier to test queries in database client
- Can optimize without touching Java code
- Version control shows SQL changes clearly

---

## 📅 EXECUTION TIMELINE

### **Sprint 1 (Week 1-2): EvidenceRepository Split**
- [ ] Day 1-2: Create `EvidenceKpiRepository`
- [ ] Day 3-4: Create `EvidenceSearchRepository`
- [ ] Day 5-6: Create `EvidenceDocumentRepository`
- [ ] Day 7-8: Refactor core `EvidenceRepository`
- [ ] Day 9-10: Update `EvidenceServiceImpl` to use new repositories

**Deliverable:** 4 focused repositories, all tests passing

---

### **Sprint 2 (Week 3-4): Query Builder + N+1 Fix**
- [ ] Day 1-3: Create `SqlFilterBuilder` utility
- [ ] Day 4-5: Refactor repositories to use builder
- [ ] Day 6-7: Fix ProfileService N+1 queries
- [ ] Day 8-10: Performance testing and optimization

**Deliverable:** Consolidated query building, 33x faster profile queries

---

### **Sprint 3 (Week 5-6): Service Orchestration**
- [ ] Day 1-3: Create `EvidenceOrchestrationService`
- [ ] Day 4-6: Move orchestration methods from `EvidenceServiceImpl`
- [ ] Day 7-8: Update controllers to use orchestration service
- [ ] Day 9-10: Transaction boundary testing

**Deliverable:** Clean service separation, clear orchestration layer

---

### **Sprint 4 (Week 7-8): Document & Risk Cleanup**
- [ ] Day 1-4: Refactor `DocumentRepository`
- [ ] Day 5-8: Refactor `RiskStoryServiceImpl`
- [ ] Day 9-10: Final integration testing

**Deliverable:** No file >500 lines, full simplification complete

---

## 📊 SUCCESS METRICS

### **Code Complexity Metrics**
- ✅ No file exceeds 500 lines
- ✅ No method exceeds 50 lines
- ✅ Cyclomatic complexity < 10 per method
- ✅ Code duplication < 5%

### **Performance Metrics**
- ✅ Profile queries < 100ms (currently 500ms+)
- ✅ Evidence search < 200ms
- ✅ No N+1 queries in profiler
- ✅ Database query count < 10 per request

### **Maintainability Metrics**
- ✅ Each repository has single responsibility
- ✅ Service methods are < 30 lines
- ✅ Test coverage > 70% for refactored code
- ✅ No SQL injection vulnerabilities

---

## 🚨 RISKS & MITIGATIONS

### **Risk 1: Breaking Changes**
**Mitigation:**
- Keep all existing tests passing
- Add integration tests before refactoring
- Use feature flags for gradual rollout

### **Risk 2: Transaction Boundary Issues**
**Mitigation:**
- Document transaction boundaries clearly
- Add `@Transactional` tests
- Use TransactionTemplate for complex scenarios

### **Risk 3: Performance Regression**
**Mitigation:**
- Performance test before/after each phase
- Monitor query counts with P6Spy
- Load test with realistic data volumes

### **Risk 4: Team Velocity Impact**
**Mitigation:**
- Allocate 20% capacity per sprint to refactoring
- Pair programming for knowledge transfer
- Document changes in ADRs (Architecture Decision Records)

---

## 💡 RECOMMENDED NEXT STEPS

1. **Review this plan** with the team
2. **Choose starting point:** Recommend Phase 1 (EvidenceRepository split)
3. **Set up branch:** `feature/simplify-evidence-repository`
4. **Allocate time:** 1-2 weeks for Phase 1
5. **Begin refactoring** with tests in place

**Want to start?** The biggest win is splitting EvidenceRepository (1,283 lines → 4 × 300 lines)

---

## 📚 REFERENCES

- Original codebase size: 224 Java files, 18,169 lines
- After cleanup: 198 Java files, 16,631 lines (-1,538 lines)
- Tech lead review: `TECH_LEAD_REVIEW.md` (if separate file created)
- Architecture decisions: To be documented in `docs/adr/`

---

**Status:** Ready to Execute
**Next Action:** Create feature branch and start Phase 1
**Owner:** Development Team
**Last Updated:** 2025-10-01
