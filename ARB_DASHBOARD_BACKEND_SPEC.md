# ARB Dashboard Backend API Specification

**Version:** 1.0
**Date:** 2025-10-13
**Author:** Frontend Team
**Status:** Ready for Implementation

---

## Table of Contents

1. [Overview](#overview)
2. [Architecture Context](#architecture-context)
3. [Existing Endpoint Modifications](#existing-endpoint-modifications)
4. [New Endpoints Required](#new-endpoints-required)
5. [Data Models](#data-models)
6. [Business Logic Requirements](#business-logic-requirements)
7. [Error Handling](#error-handling)
8. [Testing Requirements](#testing-requirements)
9. [Implementation Priority](#implementation-priority)

---

## Overview

The ARB Dashboard requires backend API enhancements to support three distinct views:
- **My Queue**: Applications with risks assigned to the current ARB user
- **My Domain**: All applications in the ARB's domain (e.g., security, data)
- **All Domains**: All applications across all domains (global view)

The frontend needs:
1. **Application watchlist** with risk aggregations and full application metadata
2. **Dashboard metrics** scoped to the current view
3. **Risk creation** capability (already exists, minor modifications needed)

---

## Architecture Context

### Current State
- ✅ Risk aggregation system exists (`domain-risks` and `risk-items`)
- ✅ Manual risk creation endpoint exists (`POST /api/v1/risk-items`)
- ✅ Basic ARB dashboard endpoint exists (`GET /api/v1/domain-risks/arb/{arbName}/dashboard`)
- ❌ Missing: Full application details in dashboard responses
- ❌ Missing: Scope-aware endpoints (my-queue vs my-domain vs all-domains)

### Integration Points
- **Application Profile Service**: Source of truth for application metadata (name, criticality, owner, transaction cycle)
- **Domain Risk Service**: Risk aggregations and priority scores
- **Risk Item Service**: Individual risk items
- **User Service**: ARB user assignments and permissions

---

## Existing Endpoint Modifications

### 1. Enhance ARB Dashboard Endpoint

**Current Endpoint:** `GET /api/v1/domain-risks/arb/{arbName}/dashboard`

**Status:** ⚠️ NEEDS ENHANCEMENT

**Problem:** The `topApplications` array returns minimal data. Frontend needs full application metadata.

#### Current Response (Incomplete)
```json
{
  "topApplications": [
    {
      "appId": "APM100001",
      "appName": null,  // ❌ Missing
      "domainRiskCount": 1,
      "totalOpenItems": 14,
      "highestPriorityScore": 55,
      "criticalDomain": "security"
    }
  ]
}
```

#### Required Enhancement
Add full application metadata by integrating with Application Profile Service.

#### Enhanced Response
```json
{
  "arbName": "security",
  "overview": {
    "totalDomainRisks": 10,
    "totalOpenItems": 126,
    "criticalCount": 2,
    "highCount": 5,
    "averagePriorityScore": 55,
    "needsImmediateAttention": 7
  },
  "domains": [...],
  "topApplications": [
    {
      "appId": "APM100001",
      "appName": "Customer Portal",
      "appCriticalityAssessment": "A",
      "transactionCycle": "Retail",
      "owner": "John Doe",
      "ownerId": "john.doe@example.com",
      "domainRiskCount": 1,
      "totalOpenItems": 14,
      "highestPriorityScore": 55,
      "criticalDomain": "security",
      "lastActivityDate": "2025-10-12T14:30:00Z"
    }
  ],
  "statusDistribution": {...},
  "priorityDistribution": {...},
  "recentActivity": {...}
}
```

#### Implementation Notes
1. **Data Source:** Query Application Profile Service for each `appId` in `topApplications`
2. **Caching:** Consider caching application metadata (criticality, owner, transaction cycle rarely change)
3. **Performance:** Batch fetch application details if possible
4. **Fallback:** If application metadata unavailable, return `null` values but include `appId`

---

## New Endpoints Required

### 2. Get ARB Applications (Watchlist)

**Endpoint:** `GET /api/v1/domain-risks/arb/{arbName}/applications`

**Status:** 🆕 NEW - Required for Application Watchlist table

**Purpose:** Return complete application list with risk aggregations for ARB dashboard watchlist.

#### Request

**HTTP Method:** `GET`

**Path:** `/api/v1/domain-risks/arb/{arbName}/applications`

**Path Parameters:**
- `arbName` (required, string): ARB identifier
  - Valid values: `security`, `data`, `operations`, `enterprise_architecture`

**Query Parameters:**
| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `scope` | string | Yes | - | Dashboard scope: `my-queue`, `my-domain`, or `all-domains` |
| `userId` | string | Conditional | - | ARB user ID. Required when `scope=my-queue` |
| `includeRisks` | boolean | No | `false` | Include detailed risks array in response |
| `page` | integer | No | `0` | Page number for pagination |
| `size` | integer | No | `100` | Page size (max 500) |

**Example Requests:**
```bash
# My Queue - applications with risks assigned to me
GET /api/v1/domain-risks/arb/security/applications?scope=my-queue&userId=security_arb_001

# My Domain - all applications in security domain
GET /api/v1/domain-risks/arb/security/applications?scope=my-domain

# All Domains - all applications across all domains
GET /api/v1/domain-risks/arb/security/applications?scope=all-domains

# With detailed risks
GET /api/v1/domain-risks/arb/security/applications?scope=my-queue&userId=security_arb_001&includeRisks=true
```

#### Response

**Status Code:** `200 OK`

**Response Body:**
```json
{
  "scope": "my-queue",
  "arbName": "security",
  "userId": "security_arb_001",
  "totalCount": 15,
  "page": 0,
  "pageSize": 100,
  "applications": [
    {
      "id": "app-internal-uuid-1",
      "appId": "APM100001",
      "name": "Customer Portal",
      "appCriticalityAssessment": "A",
      "transactionCycle": "Retail",
      "owner": "John Doe",
      "ownerId": "john.doe@example.com",
      "aggregatedRiskScore": 85,
      "totalOpenItems": 14,
      "riskBreakdown": {
        "critical": 2,
        "high": 5,
        "medium": 6,
        "low": 1
      },
      "domains": ["security", "data"],
      "hasAssignedRisks": true,
      "lastActivityDate": "2025-10-12T14:30:00Z",
      "domainRisks": [
        {
          "domainRiskId": "dr-uuid-123",
          "domain": "security",
          "status": "UNDER_ARB_REVIEW",
          "priorityScore": 85,
          "openItems": 14,
          "assignedArb": "security",
          "assignedTo": "security_arb_001",
          "assignedAt": "2025-10-10T10:00:00Z"
        }
      ],
      "risks": []
    }
  ]
}
```

#### Field Descriptions

**Application Object:**
| Field | Type | Description |
|-------|------|-------------|
| `id` | string | Internal application UUID |
| `appId` | string | External application ID (e.g., APM100001) |
| `name` | string | Application name |
| `appCriticalityAssessment` | string | Overall application criticality assessment combining CIA+S+R domains: `A`, `B`, `C`, or `D` |
| `transactionCycle` | string | Business unit/division (e.g., "Retail", "Platform", "Data", "Mobile", "Corporate", "Payments") |
| `owner` | string | Product Owner name |
| `ownerId` | string | Product Owner email/ID |
| `aggregatedRiskScore` | number | Highest priority score across all domain risks (0-100) |
| `totalOpenItems` | number | Total open risk items across all domains |
| `riskBreakdown` | object | Count of risks by severity |
| `riskBreakdown.critical` | number | Count of CRITICAL priority risks |
| `riskBreakdown.high` | number | Count of HIGH priority risks |
| `riskBreakdown.medium` | number | Count of MEDIUM priority risks |
| `riskBreakdown.low` | number | Count of LOW priority risks |
| `domains` | string[] | List of domains with risks for this app |
| `hasAssignedRisks` | boolean | True if any domain risk is assigned to the requesting user |
| `lastActivityDate` | string | ISO 8601 timestamp of most recent risk activity |
| `domainRisks` | array | Summary of domain-level risks |
| `risks` | array | Detailed risk items (only if `includeRisks=true`) |

#### Business Logic Requirements

**Scope Filtering:**

1. **my-queue:**
   - Return applications where the user has assigned domain risks
   - Only include domain risks with status: `PENDING_ARB_REVIEW`, `UNDER_ARB_REVIEW`, `AWAITING_REMEDIATION`, `IN_PROGRESS`

2. **my-domain:**
   - Return applications with domain risks in the ARB's domain
   - Only include domain risks with status: `PENDING_ARB_REVIEW`, `UNDER_ARB_REVIEW`, `AWAITING_REMEDIATION`, `IN_PROGRESS`

3. **all-domains:**
   - Return all applications with any domain risks
   - Only include domain risks with status: `PENDING_ARB_REVIEW`, `UNDER_ARB_REVIEW`, `AWAITING_REMEDIATION`, `IN_PROGRESS`

**Aggregation Calculations:**

- **aggregatedRiskScore**: Take the maximum priority score across all domain risks for the application
- **totalOpenItems**: Sum of open items across all domain risks for the application
- **riskBreakdown**: Count risk items by priority (CRITICAL, HIGH, MEDIUM, LOW), only include status = OPEN or IN_PROGRESS
- **domains**: List of unique domains that have risks for this application
- **hasAssignedRisks**: Boolean flag - true if any domain risk is assigned to the requesting user
- **lastActivityDate**: Most recent updated_at timestamp from domain risks or risk items

**Sorting:**
- Default sort: `aggregatedRiskScore DESC`, then `totalOpenItems DESC`

**Domain Risks Summary:**
For each application, include a summary of domain-level risks:
- Include domain risk ID, domain, status, priority score, open items count
- Include assignment information (assignedArb, assignedTo, assignedAt)
- Sort by priority score descending

#### Error Responses

**400 Bad Request:**
```json
{
  "timestamp": "2025-10-13T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid scope parameter. Must be one of: my-queue, my-domain, all-domains",
  "path": "/api/v1/domain-risks/arb/security/applications"
}
```

**400 Bad Request (Missing userId):**
```json
{
  "timestamp": "2025-10-13T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "userId is required when scope=my-queue",
  "path": "/api/v1/domain-risks/arb/security/applications"
}
```

**404 Not Found:**
```json
{
  "timestamp": "2025-10-13T10:00:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "ARB not found: invalid_arb_name",
  "path": "/api/v1/domain-risks/arb/invalid_arb_name/applications"
}
```

#### Implementation Notes

1. **Data Sources:**
   - Application Profile Service: application metadata (name, criticality, owner, transactionCycle)
   - Domain Risk Service: risk aggregations and priority scores
   - Risk Item Service: individual risk items for breakdown counts

2. **Performance Optimization:**
   - Consider caching application metadata (5-minute TTL)
   - Batch fetch application details where possible
   - Use appropriate indexes on frequently queried fields

3. **Data Consistency:**
   - Ensure domain risk aggregations are up-to-date (should be recalculated when risk items change)
   - Handle applications with no open domain risks (exclude from results)

4. **Pagination:**
   - Default page size: 100 applications
   - Maximum page size: 500 applications
   - Return totalCount in response for client-side pagination UI

5. **includeRisks Flag:**
   - When `includeRisks=true`, populate `risks[]` array with full risk item details
   - Default to `false` to reduce payload size
   - Consider separate endpoint if detailed risks are needed frequently

---

### 3. Get ARB Dashboard Metrics

**Endpoint:** `GET /api/v1/domain-risks/arb/{arbName}/metrics`

**Status:** 🆕 NEW - Required for Heads-Up Display (HUD)

**Purpose:** Return dashboard-level metrics scoped to current view for the HUD cards.

#### Request

**HTTP Method:** `GET`

**Path:** `/api/v1/domain-risks/arb/{arbName}/metrics`

**Path Parameters:**
- `arbName` (required, string): ARB identifier

**Query Parameters:**
| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `scope` | string | Yes | - | Dashboard scope: `my-queue`, `my-domain`, or `all-domains` |
| `userId` | string | Conditional | - | ARB user ID. Required when `scope=my-queue` |

**Example Requests:**
```bash
# My Queue metrics
GET /api/v1/domain-risks/arb/security/metrics?scope=my-queue&userId=security_arb_001

# My Domain metrics
GET /api/v1/domain-risks/arb/security/metrics?scope=my-domain

# All Domains metrics
GET /api/v1/domain-risks/arb/security/metrics?scope=all-domains
```

#### Response

**Status Code:** `200 OK`

**Response Body:**
```json
{
  "scope": "my-queue",
  "arbName": "security",
  "userId": "security_arb_001",
  "criticalCount": 12,
  "openItemsCount": 126,
  "pendingReviewCount": 45,
  "averageRiskScore": 62.5,
  "healthGrade": "C",
  "recentActivity": {
    "newRisksLast7Days": 8,
    "resolvedLast7Days": 3,
    "newRisksLast30Days": 32,
    "resolvedLast30Days": 15
  }
}
```

#### Field Descriptions

| Field | Type | Description |
|-------|------|-------------|
| `scope` | string | Dashboard scope used for filtering |
| `arbName` | string | ARB identifier |
| `userId` | string | User ID (only for my-queue scope) |
| `criticalCount` | number | Count of risk items with priority=CRITICAL and status in (OPEN, IN_PROGRESS) |
| `openItemsCount` | number | Count of risk items with status=OPEN |
| `pendingReviewCount` | number | Count of domain risks with status=PENDING_ARB_REVIEW |
| `averageRiskScore` | number | Average priorityScore across all risk items with status in (OPEN, IN_PROGRESS) |
| `healthGrade` | string | Overall health grade: `A`, `B`, `C`, `D`, `F` |
| `recentActivity.newRisksLast7Days` | number | Risk items created in last 7 days |
| `recentActivity.resolvedLast7Days` | number | Risk items resolved in last 7 days |
| `recentActivity.newRisksLast30Days` | number | Risk items created in last 30 days |
| `recentActivity.resolvedLast30Days` | number | Risk items resolved in last 30 days |

#### Business Logic Requirements

**Scope Filtering:**
Apply same filtering logic as applications endpoint (Section 2).

**Metric Calculations:**

- **criticalCount**: Count distinct risk items with priority = CRITICAL and status in (OPEN, IN_PROGRESS)
- **openItemsCount**: Count distinct risk items with status = OPEN
- **pendingReviewCount**: Count distinct domain risks with status = PENDING_ARB_REVIEW
- **averageRiskScore**: Calculate average of priorityScore field across all risk items with status in (OPEN, IN_PROGRESS)
- **healthGrade**: Calculated based on averageRiskScore:
  - A: averageRiskScore ≤ 20
  - B: averageRiskScore ≤ 40
  - C: averageRiskScore ≤ 60
  - D: averageRiskScore ≤ 80
  - F: averageRiskScore > 80

**Recent Activity:**
- **newRisksLast7Days**: Count risk items where opened_at >= (now - 7 days)
- **resolvedLast7Days**: Count risk items where resolved_at >= (now - 7 days)
- **newRisksLast30Days**: Count risk items where opened_at >= (now - 30 days)
- **resolvedLast30Days**: Count risk items where resolved_at >= (now - 30 days)

#### Error Responses

Same error handling as applications endpoint (Section 2).

#### Implementation Notes

1. **Performance:**
   - Use efficient aggregation functions
   - Consider caching with 5-minute TTL
   - Single query for all metrics preferred over multiple queries

2. **Edge Cases:**
   - If no risk items exist, return counts as `0`
   - If no risk items for average calculation, return `averageRiskScore: 0` and `healthGrade: "A"`
   - Handle timezone considerations for date calculations (use UTC)

3. **Optimization:**
   - Consider combining with applications endpoint if frontend can merge responses
   - Consider adding metrics to applications response as `meta` field to reduce API calls

---

## Data Models

### Application (Extended)

**Source:** Application Profile Service + Risk Aggregations

```typescript
interface Application {
  id: string;                    // Internal UUID
  appId: string;                 // External ID (APM100001)
  name: string;                  // Application name
  appCriticalityAssessment: 'A' | 'B' | 'C' | 'D';  // Overall assessment combining CIA+S+R
  transactionCycle: string;      // Business unit/division (e.g., "Retail", "Platform", "Data")
  owner: string;                 // Owner name
  ownerId: string;               // Owner email/ID

  // Risk Aggregations (calculated)
  aggregatedRiskScore: number;   // 0-100
  totalOpenItems: number;
  riskBreakdown: {
    critical: number;
    high: number;
    medium: number;
    low: number;
  };
  domains: string[];             // Domains with risks
  hasAssignedRisks: boolean;     // User-specific
  lastActivityDate: string;      // ISO 8601

  // Optional
  domainRisks?: DomainRiskSummary[];
  risks?: RiskItem[];
}
```

**Important Note on Application Criticality Assessment:**
The `appCriticalityAssessment` field represents the overall criticality rating for the application, combining assessments across all domains (Confidentiality, Integrity, Availability, Security, and Resilience). Valid values are `A` (highest), `B`, `C`, or `D` (lowest).

**Important Note on Transaction Cycle:**
The `transactionCycle` field represents the business unit or division that owns the application (e.g., "Retail", "Platform", "Data", "Mobile", "Corporate", "Payments"). This is NOT a time-based cycle but rather an organizational grouping.

The backend should return these fields as-is from the Application Profile Service without transformation.

### DomainRiskSummary

```typescript
interface DomainRiskSummary {
  domainRiskId: string;
  domain: string;
  status: DomainRiskStatus;
  priorityScore: number;
  openItems: number;
  assignedArb: string;
  assignedTo: string | null;
  assignedAt: string | null;
}
```

### DashboardMetrics

```typescript
interface DashboardMetrics {
  scope: 'my-queue' | 'my-domain' | 'all-domains';
  arbName: string;
  userId?: string;
  criticalCount: number;
  openItemsCount: number;
  pendingReviewCount: number;
  averageRiskScore: number;
  healthGrade: 'A' | 'B' | 'C' | 'D' | 'F';
  recentActivity: {
    newRisksLast7Days: number;
    resolvedLast7Days: number;
    newRisksLast30Days: number;
    resolvedLast30Days: number;
  };
}
```

### RiskItem (from existing API)

```typescript
interface RiskItem {
  riskItemId: string;
  domainRiskId: string;
  appId: string;
  fieldKey: string;
  profileFieldId: string;
  triggeringEvidenceId: string | null;
  trackId: string | null;
  title: string;
  description: string;
  priority: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
  severity: string;
  priorityScore: number;
  evidenceStatus: string;
  status: 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'WAIVED' | 'CLOSED';
  resolution: string | null;
  resolutionComment: string | null;
  creationType: string;
  raisedBy: string;
  openedAt: string;
  resolvedAt: string | null;
  policyRequirementSnapshot: any;
  createdAt: string;
  updatedAt: string;
}
```

---

## Business Logic Requirements

### Scope Filtering Rules

**my-queue:**
- **Filter:** Domain risks assigned to the requesting user
- **Use Case:** Personal work queue for ARB reviewer
- **Logic:** Include applications where at least one domain risk has `assigned_to = userId` and status in active statuses

**my-domain:**
- **Filter:** Domain risks in the ARB's domain
- **Use Case:** All work in ARB's area of responsibility
- **Logic:** Include applications where at least one domain risk has `domain = arbName` and status in active statuses

**all-domains:**
- **Filter:** All domain risks across all domains
- **Use Case:** Enterprise-wide visibility for senior ARB members
- **Logic:** Include all applications with domain risks in active statuses

**Active Statuses:**
For all scopes, only include domain risks and risk items with the following statuses:
- `PENDING_ARB_REVIEW`
- `UNDER_ARB_REVIEW`
- `AWAITING_REMEDIATION`
- `IN_PROGRESS`

Exclude:
- `RESOLVED`
- `WAIVED`
- `CLOSED`

### Risk Score Aggregation

**aggregatedRiskScore:**
- Take the **maximum** priority score across all domain risks for the application
- Rationale: Highest risk determines overall urgency
- Range: 0-100

**riskBreakdown:**
- Count risk items by priority enum (CRITICAL, HIGH, MEDIUM, LOW)
- Only count risk items with status = OPEN or IN_PROGRESS
- Exclude RESOLVED, WAIVED, CLOSED

**hasAssignedRisks:**
- Boolean flag: true if ANY domain risk is assigned to the requesting user
- Used for "My Queue" filtering
- Check if there exists at least one domain risk with `assigned_to = userId`

**totalOpenItems:**
- Sum of `open_items` field from all domain risks for the application
- Only include domain risks with active statuses

**domains:**
- Array of unique domain names that have active domain risks for the application
- Example: `["security", "data"]`

**lastActivityDate:**
- Most recent timestamp from either:
  - `domain_risks.updated_at` for the application's domain risks
  - `risk_items.updated_at` for the application's risk items
- Return as ISO 8601 format

---

## Error Handling

### Standard Error Response Format

```json
{
  "timestamp": "2025-10-13T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Detailed error message",
  "path": "/api/v1/domain-risks/arb/security/applications"
}
```

### HTTP Status Codes

| Code | Scenario |
|------|----------|
| 200 | Success |
| 400 | Invalid request parameters (invalid scope, missing userId, etc.) |
| 401 | Unauthorized (if authentication implemented) |
| 403 | Forbidden (user not authorized for ARB) |
| 404 | ARB not found |
| 500 | Internal server error |

### Validation Rules

**arbName:**
- Must be one of: `security`, `data`, `operations`, `enterprise_architecture`
- Case-sensitive
- Return 404 if invalid

**scope:**
- Must be one of: `my-queue`, `my-domain`, `all-domains`
- Case-sensitive
- Return 400 if invalid

**userId:**
- Required when `scope=my-queue`
- Return 400 if missing
- Validate user exists (optional, return 404 if not found)

**Pagination:**
- `page`: Must be >= 0, return 400 if negative
- `size`: Must be 1-500, return 400 if out of range

**includeRisks:**
- Must be boolean (`true` or `false`)
- Return 400 if invalid value

---

## Testing Requirements

### Unit Tests

1. **Scope Filtering:**
   - Test my-queue returns only applications with assigned risks
   - Test my-domain returns only applications in ARB's domain
   - Test all-domains returns all applications

2. **Aggregation Calculations:**
   - Test aggregatedRiskScore equals max priority score
   - Test riskBreakdown counts match expected values
   - Test hasAssignedRisks flag accuracy

3. **Metrics Calculations:**
   - Test criticalCount, openItemsCount, pendingReviewCount
   - Test averageRiskScore calculation
   - Test healthGrade assignment
   - Test recentActivity date ranges (7-day and 30-day)

4. **Edge Cases:**
   - Test empty results (no applications)
   - Test application with no risk items
   - Test zero risk score calculations
   - Test missing application metadata

### Integration Tests

1. **End-to-End Flow:**
   - Create application with domain risks
   - Assign domain risk to user
   - Call `/applications?scope=my-queue&userId=X`
   - Verify application appears in results
   - Call `/metrics?scope=my-queue&userId=X`
   - Verify metrics reflect the domain risk

2. **Cross-Service Integration:**
   - Test Application Profile Service integration
   - Test domain risk aggregation recalculation
   - Test handling of missing application metadata
   - Test handling of stale cache data

3. **Scope Transitions:**
   - Test switching between my-queue, my-domain, all-domains
   - Verify results change appropriately
   - Test userId requirement for my-queue

### Performance Tests

1. **Load Testing:**
   - Test with 1000+ applications
   - Test with 10,000+ risk items
   - Measure response time (target: < 500ms for applications, < 200ms for metrics)

2. **Concurrency:**
   - Test concurrent requests from multiple users
   - Test cache invalidation and consistency

### API Contract Tests

1. **Request Validation:**
   - Test invalid scope values
   - Test missing userId for my-queue
   - Test invalid pagination parameters
   - Test invalid arbName

2. **Response Format:**
   - Verify all required fields present
   - Verify data types match specification
   - Verify ISO 8601 date formats
   - Verify enum values (criticality, status, priority)

---

## Implementation Priority

### Phase 1: Critical Path (Week 1)
**Goal:** Enable frontend to display ARB dashboard with real data

1. ✅ **HIGHEST PRIORITY:** Implement `GET /api/v1/domain-risks/arb/{arbName}/applications`
   - Reason: Core functionality for watchlist table
   - Dependency: Application Profile Service integration
   - Estimated Effort: 3-5 days

2. ✅ **HIGH PRIORITY:** Implement `GET /api/v1/domain-risks/arb/{arbName}/metrics`
   - Reason: Required for HUD display
   - Dependency: Same data sources as applications endpoint
   - Estimated Effort: 1-2 days

### Phase 2: Enhancements (Week 2)
**Goal:** Optimize and complete functionality

3. ⚠️ **MEDIUM PRIORITY:** Enhance `GET /api/v1/domain-risks/arb/{arbName}/dashboard`
   - Reason: Nice-to-have for frontend (can use applications endpoint instead)
   - Estimated Effort: 1 day

4. ✅ **LOW PRIORITY:** Add `includeRisks` flag to applications endpoint
   - Reason: Optimization, not required for initial launch
   - Estimated Effort: 1 day

### Phase 3: Optimization (Week 3+)
**Goal:** Performance and scalability

5. Implement caching strategy (consider 5-minute TTL)
6. Performance monitoring and optimization
7. Load testing and capacity planning
8. Documentation updates

---

## API Contract Summary

### Endpoint Checklist

| Endpoint | Method | Status | Priority | Frontend Usage |
|----------|--------|--------|----------|----------------|
| `/api/v1/risk-items` | POST | ✅ Exists | - | Create Risk button |
| `/api/v1/domain-risks/arb/{arbName}/applications` | GET | 🆕 New | P0 | Application Watchlist |
| `/api/v1/domain-risks/arb/{arbName}/metrics` | GET | 🆕 New | P0 | Heads-Up Display |
| `/api/v1/domain-risks/arb/{arbName}/dashboard` | GET | ⚠️ Enhance | P1 | Optional |

### Request/Response Contract

**Applications Endpoint:**
```
GET /api/v1/domain-risks/arb/{arbName}/applications
  ?scope=my-queue|my-domain|all-domains
  &userId={userId}
  &includeRisks={true|false}
  &page={number}
  &size={number}

→ ApplicationsResponse {
    scope, arbName, userId, totalCount, page, pageSize,
    applications: Application[]
  }
```

**Metrics Endpoint:**
```
GET /api/v1/domain-risks/arb/{arbName}/metrics
  ?scope=my-queue|my-domain|all-domains
  &userId={userId}

→ DashboardMetrics {
    scope, arbName, userId,
    criticalCount, openItemsCount, pendingReviewCount,
    averageRiskScore, healthGrade,
    recentActivity { ... }
  }
```

**Create Risk Item (Existing):**
```
POST /api/v1/risk-items
{
  appId, fieldKey, profileFieldId,
  title, description, priority, createdBy, evidenceId
}

→ RiskItemResponse { riskItemId, ... }
```

---

## Questions for Backend Team

1. **Application Profile Service:**
   - What is the API for fetching application metadata?
   - Can we batch fetch multiple applications?
   - What is the data model (exact field names)?
   - What is the performance/caching strategy?

2. **Performance:**
   - What are the expected data volumes (apps, domain risks, risk items)?
   - Is caching infrastructure available (Redis, Memcached)?
   - What is the target response time SLA?

3. **Authorization:**
   - Should we implement ARB role checking?
   - Should users only see their assigned risks in my-queue?
   - Is there an admin role that can see all-domains?

4. **Deployment:**
   - What is the release timeline?
   - Can we deploy incrementally (applications endpoint first, metrics later)?
   - Is there a staging environment for frontend testing?

5. **Data Consistency:**
   - How often are domain risk aggregations recalculated?
   - Are aggregations real-time or eventual consistency?
   - How should we handle stale data scenarios?

---

## Appendix

### Example Frontend Usage

**Loading ARB Dashboard:**
```typescript
// Step 1: Load metrics
const metricsResponse = await fetch(
  `/api/v1/domain-risks/arb/security/metrics?scope=my-queue&userId=security_arb_001`
);
const metrics = await metricsResponse.json();

// Step 2: Load applications
const appsResponse = await fetch(
  `/api/v1/domain-risks/arb/security/applications?scope=my-queue&userId=security_arb_001`
);
const { applications } = await appsResponse.json();

// Step 3: Render dashboard
setDashboardData({
  applications,
  metrics,
  insights: [] // Frontend calculates insights
});
```

**Creating a Risk:**
```typescript
const createRisk = async (riskData) => {
  const response = await fetch('/api/v1/risk-items', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      appId: riskData.appId,
      fieldKey: riskData.fieldKey,
      title: riskData.title,
      description: riskData.description,
      priority: riskData.severity.toUpperCase(), // map severity to priority
      createdBy: currentUser.id,
      profileFieldId: null,
      evidenceId: null
    })
  });

  if (!response.ok) {
    throw new Error('Failed to create risk');
  }

  return await response.json();
};
```

### Sample Test Data

**Application Example 1:**
```json
{
  "appId": "APM100001",
  "name": "Customer Portal",
  "appCriticalityAssessment": "A",
  "transactionCycle": "Retail",
  "owner": "John Doe",
  "ownerId": "john.doe@example.com",
  "aggregatedRiskScore": 85,
  "totalOpenItems": 14,
  "riskBreakdown": {
    "critical": 2,
    "high": 5,
    "medium": 6,
    "low": 1
  },
  "domains": ["security", "data"],
  "hasAssignedRisks": true,
  "lastActivityDate": "2025-10-12T14:30:00Z"
}
```

**Application Example 2:**
```json
{
  "appId": "APM200002",
  "name": "Payment Processing",
  "appCriticalityAssessment": "B",
  "transactionCycle": "Platform",
  "owner": "Jane Smith",
  "ownerId": "jane.smith@example.com",
  "aggregatedRiskScore": 72,
  "totalOpenItems": 8,
  "riskBreakdown": {
    "critical": 1,
    "high": 3,
    "medium": 4,
    "low": 0
  },
  "domains": ["resilience", "operations"],
  "hasAssignedRisks": false,
  "lastActivityDate": "2025-10-11T09:15:00Z"
}
```

**Dashboard Metrics:**
```json
{
  "scope": "my-queue",
  "arbName": "security",
  "userId": "security_arb_001",
  "criticalCount": 12,
  "openItemsCount": 126,
  "pendingReviewCount": 45,
  "averageRiskScore": 62.5,
  "healthGrade": "C",
  "recentActivity": {
    "newRisksLast7Days": 8,
    "resolvedLast7Days": 3,
    "newRisksLast30Days": 32,
    "resolvedLast30Days": 15
  }
}
```

---

## Change Log

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2025-10-13 | Frontend Team | Initial specification |
| 1.1 | 2025-10-13 | Frontend Team | Updated data model: Changed `criticality` to `appCriticalityAssessment` (A/B/C/D); Changed `transactionCycle` to represent business unit instead of time cycle |

---

## Approval

**Frontend Lead:** _________________ Date: _______

**Backend Lead:** _________________ Date: _______

**Product Owner:** _________________ Date: _______
