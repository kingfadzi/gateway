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
├── risk/               # Risk story creation and management
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
