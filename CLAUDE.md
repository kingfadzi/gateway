# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Control Plane Gateway** - Spring Boot 3.5 backend service for automated application governance and compliance tracking. The system evaluates applications against policy requirements, manages evidence collection, and tracks compliance status across multiple domains (Security, Integrity, Availability, Resilience, Confidentiality).

**Tech Stack:**
- Java 21 + Spring Boot 3.5.3
- PostgreSQL (running on `helios:5432`)
- Maven build system with wrapper (`./mvnw`)
- JaCoCo for code coverage
- H2 for integration tests

## Build & Test Commands

### Basic Operations
```bash
# Build the project
./mvnw clean install

# Run tests
./mvnw test

# Run single test class
./mvnw test -Dtest=SqlFilterBuilderTest

# Run single test method
./mvnw test -Dtest=SqlFilterBuilderTest#testBuildCriticalityFilter

# Run application locally
./mvnw spring-boot:run

# Generate code coverage report
./mvnw test jacoco:report
# View at: target/site/jacoco/index.html
```

### Test Profiles
- **Default profile:** Uses H2 in-memory database
- **Test profile:** Integration tests with real PostgreSQL (configured in `AbstractIntegrationTest`)
- Tests in `src/test/java/com/example/gateway/common/` extend `AbstractIntegrationTest` for database tests

## Architecture Overview

### Layered Architecture
```
Controllers → Services → Repositories
                ↓
        Orchestration Services (multi-service workflows)
```

**Key architectural patterns:**
1. **Repository Split Pattern** - Large repositories split by responsibility (see `EvidenceRepository` → 4 focused repos)
2. **Orchestration Layer** - Complex multi-service operations isolated in `*OrchestrationService` classes
3. **Batch Loading** - Use batch queries to prevent N+1 query problems (see `ProfileServiceImpl`)
4. **SQL Filter Builder** - Centralized query building via `SqlFilterBuilder` utility

### Core Domain Model

**Primary Entities:**
- **Application** - App being governed (`app_id`, criticality ratings, profile)
- **Profile** - Dynamic compliance requirements per app (derived from app ratings + registry)
- **Evidence** - Documents/artifacts proving compliance (links, files)
- **Requirement** - OPA policy-driven compliance requirements (allOf/anyOf/oneOf logic)
- **Claim** - Submission of evidence against a requirement
- **Track** - Release/change tracking entity

**Key Relationships:**
- Application → Profile (1:1, auto-created via `AutoProfileService`)
- Profile → ProfileFields (1:N, derived from `profile-fields.registry.yaml`)
- Evidence → EvidenceFieldLink (N:M via junction table)
- Requirement → RequirementPart → ReuseCandidate (nested structure)

### Package Structure

```
com.example.gateway/
├── application/          # Application CRUD, KPIs, attestation
├── profile/             # Profile management, field registry, auto-derivation
├── evidence/            # Evidence CRUD, search, KPIs, reuse suggestions
│   ├── service/
│   │   ├── EvidenceService            # Core CRUD
│   │   ├── EvidenceOrchestrationService  # Multi-service workflows
│   │   └── EvidenceReuseService       # Reuse candidate matching
│   └── repository/
│       ├── EvidenceRepository          # Core CRUD (1,097 lines)
│       ├── EvidenceKpiRepository       # KPI queries (402 lines)
│       ├── EvidenceSearchRepository    # Search/filter (251 lines)
│       ├── EvidenceDocumentRepository  # Document ops (73 lines)
│       └── SqlFilterBuilder           # Reusable SQL filters (176 lines)
├── requirements/        # OPA-driven requirements assembly + reuse decoration
├── policy/              # OPA client, policy evaluation
├── document/           # Document metadata (GitLab, Confluence), URL validation
├── risk/               # Risk aggregation (domain risks + risk items)
│   ├── model/          # DomainRisk, RiskItem entities + enums
│   ├── repository/     # Domain & item repositories with ARB/PO queries
│   ├── service/        # Aggregation, priority calc, ARB routing
│   ├── controller/     # REST API for ARB & PO views
│   └── dto/            # Request/response DTOs + mapper
├── track/              # Release/change tracking
└── registry/           # Compliance context, risk config
```

### Critical Services

**ProfileServiceImpl** (`profile/service/ProfileServiceImpl.java`)
- Assembles profile from app ratings + field registry
- **Performance:** Uses batch loading to avoid N+1 queries (33x faster: 500ms → 15ms)
- Creates profile graph structure with evidence status

**RequirementsService** (`requirements/service/RequirementsService.java`)
- Fetches OPA policy decision for app/release
- Decorates requirements with reuse candidates via `EvidenceReuseService`
- Returns FE-ready nested structure (allOf/anyOf/oneOf parts)

**EvidenceOrchestrationService** (`evidence/service/EvidenceOrchestrationService.java`)
- Handles multi-service workflows (create evidence + document + link)
- Clear `@Transactional` boundaries
- Coordinates DocumentService, EvidenceService, EvidenceFieldLinkService

**AutoProfileService** (`profile/service/AutoProfileService.java`)
- Auto-creates profiles on app creation
- Derives field requirements from `profile-fields.registry.yaml` + app ratings
- Triggered by feature flag: `autoprofile.enabled=true`

### Risk Aggregation Architecture (New)

**Overview:** Domain-level risk aggregation system that groups evidence-level risks by domain and routes to appropriate Architecture Review Boards (ARBs).

**Flow:**
```
Evidence Submission → RiskItem (evidence-level)
                          ↓
                    DomainRisk (domain-level aggregation)
                          ↓
                    ARB Assignment (based on domain)
                          ↓
        ┌──────────────────────┬──────────────────────┐
        │   ARB/SME View       │     PO/Dev View      │
        │ (domain aggregates)  │  (evidence items)    │
        └──────────────────────┴──────────────────────┘
```

**Key Components:**

**1. Data Model (`risk/model/`)**
- **DomainRisk** - One per app+domain (e.g., app-123/security)
  - Aggregates: `totalItems`, `openItems`, `highPriorityItems`
  - Priority score: 0-100 scale (auto-calculated)
  - Status: `PENDING_ARB_REVIEW` → `UNDER_ARB_REVIEW` → `RESOLVED`
  - ARB assignment: Based on domain (security → security_arb)

- **RiskItem** - Individual evidence-level risk
  - Links to: evidence, field, profile field, domain risk
  - Priority: `CRITICAL` (90-100), `HIGH` (70-89), `MEDIUM` (40-69), `LOW` (0-39)
  - Status: `OPEN` → `IN_PROGRESS` → `RESOLVED`/`WAIVED`/`CLOSED`
  - Score calculation: Base priority × evidence status multiplier

- **Enums:**
  - `RiskPriority` - CRITICAL/HIGH/MEDIUM/LOW with score mappings
  - `DomainRiskStatus` - 7 states for domain lifecycle
  - `RiskItemStatus` - 5 states for item lifecycle

**2. Services (`risk/service/`)**

**RiskPriorityCalculator**
- Calculates priority scores (0-100) from priority enum + evidence status
- Evidence multipliers: missing=2.5x, non_compliant=2.3x, expired=2.0x, approved=1.0x
- Domain score = max item score + bonuses (high-priority items, volume)

**ArbRoutingService**
- Routes risks to ARBs based on field's `derived_from` value
- Maps: `security_rating` → `security_arb`, `integrity_rating` → `integrity_arb`
- Calculates domain by stripping `_rating` suffix
- Configuration in: `profile-fields.registry.yaml` → `arb_routing` section

**DomainRiskAggregationService**
- Creates/retrieves domain risks (one per app+domain, enforced by UNIQUE constraint)
- Adds risk items to domains (triggers auto-recalculation)
- Recalculates on every update:
  - Count: totalItems, openItems, highPriorityItems
  - Score: priorityScore with bonuses
  - Severity: overallPriority, overallSeverity
- Auto-transitions status:
  - → `RESOLVED` when all items closed
  - → `IN_PROGRESS` when new items added to resolved risk

**RiskAutoCreationServiceImpl** (refactored)
- Triggers on evidence submission
- Creates RiskItem (not RiskStory)
- Gets/creates DomainRisk for app+domain
- Calculates priority score with evidence multipliers
- Returns ARB assignment in response

**3. REST API (`risk/controller/`)**

**DomainRiskController** - ARB/SME View
```
GET    /api/v1/domain-risks/arb/{arbName}           → Get risks for ARB (filterable)
GET    /api/v1/domain-risks/arb/{arbName}/summary   → Dashboard statistics
GET    /api/v1/domain-risks/{domainRiskId}          → Get specific domain risk
GET    /api/v1/domain-risks/{domainRiskId}/items    → Drill down to items
GET    /api/v1/domain-risks/app/{appId}             → Get all for app
```

**RiskItemController** - PO/Developer View
```
GET    /api/v1/risk-items/app/{appId}                    → All items (prioritized)
GET    /api/v1/risk-items/app/{appId}/status/{status}    → Filter by status
GET    /api/v1/risk-items/{riskItemId}                   → Get specific item
PATCH  /api/v1/risk-items/{riskItemId}/status            → Update status
GET    /api/v1/risk-items/field/{fieldKey}               → Items by field
GET    /api/v1/risk-items/evidence/{evidenceId}          → Items by evidence
```

**4. Database Schema (`V2__create_domain_risk_tables.sql`)**

**domain_risk table:**
- Primary key: `domain_risk_id` (UUID)
- Unique constraint: `(app_id, domain)` - ensures one per app+domain
- 11 indexes for query optimization
- Auto-update triggers for `updated_at`

**risk_item table:**
- Primary key: `risk_item_id` (UUID)
- Foreign key: `domain_risk_id` (CASCADE on delete)
- 8 indexes including composite for deduplication
- JSONB column: `policy_requirement_snapshot`

**5. Priority Scoring Formula**

```
Item Score = Base Priority × Evidence Multiplier
  - Base: CRITICAL=40, HIGH=30, MEDIUM=20, LOW=10
  - Multiplier: missing=2.5, non_compliant=2.3, expired=2.0, approved=1.0
  - Result: 0-100 (capped at 100)

Domain Score = Max(Item Scores) + Bonuses
  - High priority bonus: min(10, highPriorityCount × 2)
  - Volume bonus: min(5, (openCount - 3))
  - Result: 0-100 (capped at 100)
```

**6. Registry Configuration**

**ARB Routing** (`profile-fields.registry.yaml`):
```yaml
arb_routing:
  security_rating: security_arb
  integrity_rating: integrity_arb
  availability_rating: availability_arb
  resilience_rating: resilience_arb
  confidentiality_rating: confidentiality_arb
  app_criticality_assessment: governance_arb
```

**Field Priority** (per criticality):
```yaml
- key: encryption_at_rest
  derived_from: security_rating
  rule:
    A1: { value: required, label: "Required", ttl: 90d,
          requires_review: true, priority: CRITICAL }
    A2: { value: required, label: "Required", ttl: 90d,
          requires_review: true, priority: HIGH }
    B:  { value: required, label: "Required", ttl: 180d,
          requires_review: true, priority: MEDIUM }
```

**7. Testing**

**Insomnia Collection:** `insomnia-risk-aggregation-api.json`
- 17 pre-configured requests
- Environment variables for easy customization
- Example requests for all workflows

**API Documentation:** `RISK_AGGREGATION_API.md`
- Complete endpoint reference
- Request/response examples
- Status enum definitions
- Error handling

**Testing Guide:** `TESTING_GUIDE.md`
- 7 end-to-end scenarios
- Validation checklist
- Common issues & fixes

**8. Key Design Decisions**

**Why domain-level aggregation?**
- Reduces noise: ARBs see 1 risk per domain instead of N evidence items
- Better prioritization: Aggregate scores consider all items in domain
- Clearer ownership: Each domain routes to specific ARB

**Why separate views?**
- ARBs need strategic view (domain-level)
- POs need tactical view (evidence-level)
- Different APIs prevent data overload

**Why auto-recalculation?**
- Always consistent: Aggregations never stale
- Real-time: Status changes immediately reflected
- Transactional: Updates atomic with item changes

**Performance Considerations:**
- Indexes on all query paths (ARB, app, domain, field)
- Batch loading prevents N+1 queries
- Domain risks cached at service layer
- Unique constraints prevent duplicate aggregations

### SQL & Database Patterns

**Use `SqlFilterBuilder` for reusable filters:**
```java
// Good: Centralized filter building
SqlFilterBuilder builder = new SqlFilterBuilder();
builder.addCriticalityFilter(sql, params, criticality);
builder.addDomainFilter(sql, params, domain);
builder.addSearchFilter(sql, params, search);

// Bad: Duplicate filter logic in every method
if (criticality != null && !criticality.isEmpty()) {
    String[] values = criticality.split(",");
    sql.append(" AND app.criticality IN (");
    // ... 20 lines of boilerplate
}
```

**Prevent N+1 queries with batch loading:**
```java
// Good: Single batch query
List<String> fieldIds = fields.stream().map(Field::getId).toList();
List<Evidence> evidence = repository.findByProfileFieldsBatch(fieldIds);
Map<String, Evidence> evidenceMap = evidence.stream()
    .collect(Collectors.groupingBy(Evidence::getProfileFieldId));

// Bad: Query in loop (N+1 problem)
for (Field field : fields) {
    Evidence evidence = repository.findByProfileFieldId(field.getId()); // ❌
}
```

**Use Java text blocks for SQL (Java 15+):**
```java
String sql = """
    SELECT e.*, d.title, d.uri
    FROM evidence e
    LEFT JOIN document d ON e.document_id = d.id
    WHERE e.app_id = :appId
    ORDER BY e.created_at DESC
    """;
```

### Configuration Files

**application.yml** (`src/main/resources/application.yml`)
- Database: PostgreSQL at `helios:5432/lct_data`
- OPA: Policy engine at `mars:8181`
- External APIs: GitLab, Confluence tokens via env vars
- Feature flags: `features.reuseSuggestions`, `autoprofile.enabled`

**profile-fields.registry.yaml** (`src/main/resources/profile-fields.registry.yaml`)
- Defines 50+ compliance fields with rules per rating (A/B/C/D or A1/A2/B/C/D or 0/1/2/3/4)
- Structure: `key`, `label`, `derived_from`, `rule` (rating → value/label/ttl/requires_review)
- Used by `AutoProfileService` to derive profile fields
- Compliance frameworks mapped: Internal, SOC2, PCI_DSS

### API Patterns

**REST Controller Pattern:**
```java
@RestController
@RequestMapping("/api/apps/{appId}/evidence")
public class EvidenceController {

    @GetMapping
    public PageResponse<Evidence> list(
        @PathVariable String appId,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int pageSize
    ) {
        return service.getEvidenceByApp(appId, page, pageSize);
    }
}
```

**Error handling:** Global via `@ControllerAdvice` in `GlobalExceptionHandler`

### Key API Endpoints

**Core workflows (see README.md for curl examples):**
- `POST /api/apps` - Create app (auto-creates profile)
- `GET /api/apps/{appId}/requirements` - Get requirements with reuse suggestions
- `POST /api/apps/{appId}/evidence` - Create evidence (JSON or multipart)
- `POST /api/apps/{appId}/claims` - Submit claim (attach evidence to requirement)
- `GET /internal/evidence/reuse` - Find reusable evidence for a field
- `POST /api/evidence/{evidenceId}/review` - Review evidence (approve/reject/supersede)

### Testing Strategy

**Current Status:** ⚠️ Low test coverage (4 test files for 205 production files)

**Existing tests:**
- `SqlFilterBuilderTest` - 22 passing tests for SQL filter building
- Integration tests use H2 in-memory database
- Repository tests extend `AbstractRepositoryTest`
- Service tests extend `AbstractIntegrationTest`

**When adding tests:**
1. Use `@DataJpaTest` for repository tests
2. Use `@SpringBootTest` for integration tests
3. Mock external dependencies (OPA, GitLab, Confluence)
4. Use AssertJ fluent assertions

### Recent Refactoring Work

**Context:** Major simplification effort completed (see `SIMPLIFICATION_PLAN.md`):
- Split 1,283-line `EvidenceRepository` into 4 focused repositories
- Extracted `SqlFilterBuilder` (eliminated 607 lines of duplicate code)
- Fixed N+1 query problem (33x performance gain)
- Created orchestration service layer
- Extracted row mapper utilities

**Key Learnings:**
1. Prefer batch loading over loops with queries
2. Extract shared SQL building logic early
3. Separate CRUD operations from multi-service orchestration
4. Keep services focused (Single Responsibility Principle)

### Embedded Audit Framework

**AuditKit** (`dev.controlplane.auditkit` package)
- In-tree audit logging framework
- Annotations: `@Audited` on service methods, `@AuditRedact` on sensitive fields
- Context holders: `CorrelationContextHolder`, `ActorContextHolder`, `PolicyDecisionContextHolder`
- Sink client: HTTP-based audit event shipping (`audit.sink.url`)
- Auto-configured via `AuditAutoConfiguration`

### External Integrations

**OPA (Open Policy Agent):**
- Base URL: Configured via `opa.base-url`
- Evaluates policy at `/v1/data/change_arb/result`
- Input: App profile + release context
- Output: `PolicyDecision` with nested requirements structure

**Platform APIs:**
- **GitLab:** Token via `GITLAB_API_TOKEN`, uses `GitLabMetadataService`
- **Confluence:** Token via `CONFLUENCE_API_TOKEN`, uses `ConfluenceMetadataService`
- Centralized HTTP client: `PlatformApiClient` utility (84 lines)

### Common Patterns to Follow

1. **Use interfaces for services** - `EvidenceService` interface → `EvidenceServiceImpl`
2. **Constructor injection** - Prefer final fields with constructor injection over `@Autowired`
3. **Records for DTOs** - Use Java records for immutable DTOs (e.g., `PolicyDecision`)
4. **MapSqlParameterSource** - Use for named parameters in JDBC queries
5. **@Transactional** - Mark orchestration methods with clear transaction boundaries
6. **Pagination** - Return `PageResponse<T>` for list endpoints
7. **Explicit imports** - Avoid wildcard imports (ongoing cleanup)

### Important Environment Variables

```bash
GITLAB_API_TOKEN      # GitLab API access token
CONFLUENCE_USERNAME   # Confluence username
CONFLUENCE_API_TOKEN  # Confluence API token
```

### Database Schema Notes

- Primary key convention: `{table}_id` as UUID (e.g., `evidence_id`, `app_id`)
- Audit columns: `created_at`, `updated_at`, `created_by`, `updated_by`
- Soft delete: `deleted_at` (null = active)
- Evidence status: `active`, `superseded`, `revoked`
- Link status: `ATTACHED`, `PENDING_REVIEW`, `APPROVED`, `USER_ATTESTED`, `REJECTED`

### Development Workflow

1. **Before refactoring:** Read existing tests, understand current behavior
2. **Repository changes:** Update corresponding service + controller
3. **SQL changes:** Test with actual PostgreSQL, not just H2
4. **Performance:** Profile queries with Spring JDBC logging (`org.springframework.jdbc.core: DEBUG`)
5. **Commits:** Small, focused commits with clear messages
6. **Documentation:** Update relevant docs (SIMPLIFICATION_PLAN.md, CLAUDE.md)

### Git Commit Conventions

- Follow conventional commit format: `type(scope): description`
- **DO NOT add attribution footers** - No "Generated with Claude Code" or "Co-Authored-By: Claude" lines
- Keep commit messages concise and focused on the "why"
- Types: `feat`, `fix`, `refactor`, `docs`, `test`, `chore`, `perf`
- Example: `feat(risk): add domain-level risk aggregation`

### Common Gotchas

1. **Field key resolution:** Support both dotted keys (`security.encryption_at_rest`) and stored keys (`encryption_at_rest`)
2. **Evidence deduplication:** Same payload returns existing `evidenceId`
3. **Supersession:** Approving new evidence for same field auto-supersedes previous
4. **Profile auto-creation:** Triggered on app creation if `autoprofile.enabled=true`
5. **Reuse suggestions:** Filtered by `maxAgeDays`, `asOf` timestamp, and approval status
6. **Test profiles:** Use `@ActiveProfiles("test")` for PostgreSQL integration tests

### OpenAPI/Swagger

- Available at: `http://localhost:8080/swagger-ui.html`
- API docs auto-generated from controller annotations
- Configured via `OpenApiConfig`
