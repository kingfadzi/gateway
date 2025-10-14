# SME Dashboard API Specification

## Overview

The SME (Subject Matter Expert) Dashboard provides a centralized interface for security, integrity, confidentiality, availability, and resilience domain experts to review evidence submissions and manage compliance risks.

**Primary User Persona**: Domain SMEs (e.g., Security ARB members, Integrity reviewers)

**Key Responsibilities**:
1. Review evidence submissions requiring expert validation
2. Approve or reject evidence based on compliance requirements
3. Monitor risk items triggered by evidence submissions
4. Track domain-level risk aggregations across applications
5. Collaborate via comments on risk items

---

## Dashboard Layout Recommendations

### Top-Level Navigation

```
┌─────────────────────────────────────────────────────────────┐
│  SME Dashboard - Security ARB                               │
├─────────────────────────────────────────────────────────────┤
│  [Pending Reviews] [My Risks] [Domain Risks] [Analytics]   │
└─────────────────────────────────────────────────────────────┘
```

### Primary Views

1. **Pending Reviews** - Evidence awaiting SME approval (high priority)
2. **My Risks** - Risk items assigned to this SME's domain
3. **Domain Risks** - Aggregated risks by domain (strategic view)
4. **Analytics** - KPIs, trends, workload metrics

---

## Core Workflows

### Workflow 1: Review Evidence Submissions

**User Story**: As an SME, I need to review evidence submissions for my domain and approve or reject them based on compliance requirements.

**Steps**:
1. View list of pending evidence submissions
2. Filter by field, app, priority, or submission date
3. Click on evidence to see details
4. Review linked documentation/artifacts
5. Approve or reject with comments
6. View updated risk priority after decision

---

### Workflow 2: Monitor Domain Risks

**User Story**: As an SME, I need to see all domain-level risks for my ARB to prioritize my team's work.

**Steps**:
1. View domain risks assigned to my ARB
2. Sort by priority score (highest urgency first)
3. Filter by status (pending, under review, in progress)
4. Drill down from domain → risk items → evidence
5. Track resolution progress over time

---

### Workflow 3: Manage Risk Items

**User Story**: As an SME, I need to track individual risk items and collaborate with developers on remediation.

**Steps**:
1. View risk items for my domain
2. Filter by app, field, status, priority
3. Add comments for guidance/discussion
4. Monitor status changes (open → in progress → resolved)
5. Verify fixes before closing

---

## API Endpoints Reference

### Base URL
```
http://localhost:8181
```

**Note**: The service runs on port 8181 (lct_data_v2 database)

---

## 1. Evidence Review Endpoints

### 1.1 Get Pending SME Review Evidence

**Purpose**: Retrieve all evidence submissions awaiting SME approval for a specific reviewer.

**Endpoint**: `GET /api/evidence/pending-sme-review`

**Query Parameters**:
- `assignedSme` (required): SME email/identifier
- `page` (optional): Page number (default: 0)
- `size` (optional): Items per page (default: 100)

**Example Request**:
```bash
GET /api/evidence/pending-sme-review?assignedSme=sme@example.com
```

**Response Schema**:
```json
[
  {
    "evidenceId": "evidence-789",
    "appId": "app-001",
    "appName": "Payment Service",
    "profileFieldId": "pf-123",
    "fieldKey": "encryption_at_rest",
    "fieldLabel": "Encryption at Rest",
    "uri": "https://confluence.example.com/security/encryption-policy.pdf",
    "type": "link",
    "documentTitle": "Encryption Policy v3",
    "linkStatus": "PENDING_SME_REVIEW",
    "submittedBy": "developer@example.com",
    "linkedAt": "2025-10-12T10:00:00Z",
    "validFrom": "2025-10-01T00:00:00Z",
    "validUntil": "2026-10-01T00:00:00Z",
    "priority": "CRITICAL",
    "domain": "security",
    "criticality": "A1"
  }
]
```

**Frontend Display**:
- **List View**: Show as cards or table rows
- **Sort By**: linkedAt (newest first), priority (critical first)
- **Filters**: fieldKey, appId, priority, date range
- **Actions**: Click to review details, bulk approve/reject

---

### 1.2 Approve Evidence

**Purpose**: Approve evidence submission, triggering risk recalculation with 0.5x priority multiplier.

**Endpoint**: `POST /api/evidence/{evidenceId}/review`

**Query Parameters**:
- `profileFieldId` (required): Profile field ID

**Request Body**:
```json
{
  "action": "approve",
  "reviewerId": "sme@example.com",
  "reviewComment": "Encryption policy meets all requirements. AES-256 implementation confirmed."
}
```

**Response Schema**:
```json
{
  "evidenceId": "evidence-789",
  "profileFieldId": "pf-123",
  "linkStatus": "APPROVED",
  "linkedBy": "developer@example.com",
  "linkedAt": "2025-10-12T10:00:00Z",
  "reviewedBy": "sme@example.com",
  "reviewedAt": "2025-10-12T14:30:00Z",
  "reviewComment": "Encryption policy meets all requirements. AES-256 implementation confirmed."
}
```

**Side Effects**:
- Evidence status changes: `PENDING_SME_REVIEW` → `APPROVED`
- Risk item priority score **drops by ~50%** (1.0x → 0.5x multiplier)
- Domain risk aggregations recalculated
- Timestamps updated on risk items and domain risks

**Frontend Display**:
- **Success Message**: "Evidence approved. Risk priority reduced from 100 to 50."
- **Remove from pending list**: Item disappears from "Pending Reviews"
- **Update risk cards**: Show new priority scores

---

### 1.3 Reject Evidence

**Purpose**: Reject evidence submission, keeping risk priority high to signal developer rework needed.

**Endpoint**: `POST /api/evidence/{evidenceId}/review`

**Query Parameters**:
- `profileFieldId` (required): Profile field ID

**Request Body**:
```json
{
  "action": "reject",
  "reviewerId": "sme@example.com",
  "reviewComment": "Documentation insufficient. Please provide detailed key rotation procedures and access control policies."
}
```

**Response Schema**: Same as approve response, but with `linkStatus: "REJECTED"`

**Side Effects**:
- Evidence status: `PENDING_SME_REVIEW` → `REJECTED`
- Risk priority score: Minor reduction (0.9x multiplier) - still high urgency
- Developer notified to resubmit

**Frontend Display**:
- **Warning Message**: "Evidence rejected. Developer will be notified."
- **Show rejection reason**: Display in evidence history
- **Keep in tracking list**: Move to "Recently Reviewed" section

---

### 1.4 Search Evidence (Advanced Filters)

**Purpose**: Search evidence with multiple filters for workbench views.

**Endpoint**: `GET /api/evidence/search`

**Query Parameters**:
- `linkStatus` (optional): `PENDING_SME_REVIEW`, `APPROVED`, `REJECTED`
- `appId` (optional): Filter by application
- `fieldKey` (optional): Filter by field
- `assignedSme` (optional): Filter by assigned SME
- `criticality` (optional): Filter by app criticality (A1, A2, B, C, D)
- `domain` (optional): Filter by domain (security, integrity, etc.)
- `search` (optional): Text search in evidence titles/descriptions
- `enhanced` (optional): `true` for workbench-enriched data
- `page` (optional): Page number
- `size` (optional): Items per page

**Example Request**:
```bash
GET /api/evidence/search?linkStatus=PENDING_SME_REVIEW&domain=security&criticality=A1&enhanced=true
```

**Response Schema**: Array of `EnhancedEvidenceSummary` or `WorkbenchEvidenceItem` objects

**Frontend Use Cases**:
- **Filter Panel**: Add dropdowns/chips for each parameter
- **Search Bar**: Text search across evidence
- **Saved Filters**: "High Priority Pending", "My Recent Reviews"

---

## 2. Risk Item Endpoints (Evidence-Level)

### 2.1 Get Risk Items for App

**Purpose**: View all risk items for a specific application, prioritized by score.

**Endpoint**: `GET /api/v1/risk-items/app/{appId}`

**Example Request**:
```bash
GET /api/v1/risk-items/app/app-001
```

**Response Schema**:
```json
[
  {
    "riskItemId": "item-456",
    "domainRiskId": "dr-123",
    "appId": "app-001",
    "fieldKey": "encryption_at_rest",
    "profileFieldId": "pf-789",
    "triggeringEvidenceId": "evidence-001",
    "title": "Compliance risk: encryption_at_rest",
    "description": "Evidence for encryption_at_rest requires review due to A1 rating configuration",
    "priority": "CRITICAL",
    "severity": "critical",
    "priorityScore": 100,
    "evidenceStatus": "submitted",
    "status": "OPEN",
    "creationType": "SYSTEM_AUTO_CREATION",
    "raisedBy": "SYSTEM_AUTO_CREATION",
    "openedAt": "2025-10-11T14:30:00Z",
    "resolvedAt": null,
    "createdAt": "2025-10-11T14:30:00Z",
    "updatedAt": "2025-10-11T14:30:00Z"
  }
]
```

**Sort Order**: `priorityScore DESC` (highest urgency first)

**Frontend Display**:
- **Risk Card Grid**: Show as cards with priority badges
- **Color Coding**: Critical=red, High=orange, Medium=yellow, Low=blue
- **Priority Score Bar**: Visual indicator (0-100)
- **Status Badge**: OPEN, IN_PROGRESS, RESOLVED

---

### 2.2 Get Risk Items by Status

**Purpose**: Filter risk items by their current status.

**Endpoint**: `GET /api/v1/risk-items/app/{appId}/status/{status}`

**Path Parameters**:
- `status`: `OPEN`, `IN_PROGRESS`, `RESOLVED`, `WAIVED`, `CLOSED`

**Example Request**:
```bash
GET /api/v1/risk-items/app/app-001/status/OPEN
```

**Response Schema**: Array of `RiskItemResponse` objects (same as 2.1)

**Frontend Use Cases**:
- **Tab Navigation**: Tabs for each status
- **Badge Counts**: Show count per status (e.g., "OPEN (5)")
- **Quick Filters**: "Show only open items"

---

### 2.3 Get Risk Item by ID

**Purpose**: View detailed information for a specific risk item.

**Endpoint**: `GET /api/v1/risk-items/{riskItemId}`

**Example Request**:
```bash
GET /api/v1/risk-items/item-456
```

**Response Schema**: Single `RiskItemResponse` object with full details including `policyRequirementSnapshot`

**Frontend Display**:
- **Detail Modal/Page**: Full-screen view with all metadata
- **Evidence Link**: Click to view triggering evidence
- **Policy Context**: Show compliance framework requirements
- **Timeline**: Visual timeline of status changes

---

### 2.4 Get Risk Items by Field

**Purpose**: View all risk items for a specific compliance field (field-specific analysis).

**Endpoint**: `GET /api/v1/risk-items/field/{fieldKey}`

**Example Request**:
```bash
GET /api/v1/risk-items/field/encryption_at_rest
```

**Response Schema**: Array of `RiskItemResponse` objects

**Frontend Use Cases**:
- **Field Dashboard**: Dedicated view per compliance field
- **Trend Analysis**: Track field-specific issues over time
- **Pattern Detection**: Identify recurring problems

---

### 2.5 Get Risk Items by Evidence

**Purpose**: Trace from evidence submission to all risk items it triggered.

**Endpoint**: `GET /api/v1/risk-items/evidence/{evidenceId}`

**Example Request**:
```bash
GET /api/v1/risk-items/evidence/evidence-001
```

**Response Schema**: Array of `RiskItemResponse` objects

**Frontend Use Cases**:
- **Evidence Impact View**: Show risks created by approving/rejecting evidence
- **Traceability**: Full audit trail from submission to resolution
- **Before/After Comparison**: Show priority changes after evidence review

---

### 2.6 Add Comment to Risk Item

**Purpose**: Add guidance, discussion, or notes to a risk item.

**Endpoint**: `POST /api/v1/risk-items/{riskItemId}/comments`

**Request Body**:
```json
{
  "commentType": "REVIEW",
  "commentText": "Reviewed encryption configuration. Recommend implementing AES-256 with proper key management. See NIST guidelines: https://...",
  "commentedBy": "sme@example.com",
  "isInternal": false
}
```

**Response Schema**:
```json
{
  "commentId": "comment-111",
  "riskItemId": "item-456",
  "commentType": "REVIEW",
  "commentText": "Reviewed encryption configuration...",
  "commentedBy": "sme@example.com",
  "commentedAt": "2025-10-12T16:15:00Z",
  "isInternal": false,
  "createdAt": "2025-10-12T16:15:00Z"
}
```

**Comment Types**:
- `GENERAL`: General discussion
- `REVIEW`: SME review feedback
- `STATUS_CHANGE`: Related to status updates
- `RESOLUTION`: Resolution notes

**Frontend Display**:
- **Comment Thread**: Chronological discussion view
- **Rich Text Editor**: Support markdown/links
- **Internal Toggle**: Checkbox for "Internal ARB notes only"
- **Notification**: Alert developers when SME adds comment

---

### 2.7 Get Comments for Risk Item

**Purpose**: Retrieve discussion history for a risk item.

**Endpoint**: `GET /api/v1/risk-items/{riskItemId}/comments`

**Query Parameters**:
- `includeInternal` (optional): Include internal ARB notes (default: false)

**Example Request**:
```bash
GET /api/v1/risk-items/item-456/comments?includeInternal=true
```

**Response Schema**: Array of comment objects

**Frontend Display**:
- **Timeline View**: Chronological with avatars
- **Filter Toggle**: Show/hide internal comments
- **Collapsible**: Expand/collapse long threads

---

## 3. Domain Risk Endpoints (Strategic View)

### 3.1 Get Domain Risks for ARB

**Purpose**: View all domain-level risk aggregations assigned to a specific ARB.

**Endpoint**: `GET /api/v1/domain-risks/arb/{arbName}`

**Query Parameters**:
- `arbName` (path): ARB identifier - **Use short names**: `security`, `data`, `operations`, `enterprise_architecture`
- `status` (query, optional): Comma-separated statuses
  - Default: `PENDING_ARB_REVIEW,UNDER_ARB_REVIEW,AWAITING_REMEDIATION,IN_PROGRESS`

**Example Request**:
```bash
GET /api/v1/domain-risks/arb/security?status=PENDING_ARB_REVIEW,UNDER_ARB_REVIEW
```

**Important**: ARB names are **short format** (from registry):
- `security` - Security domain
- `data` - Confidentiality & Integrity domains
- `operations` - Availability & Resilience domains
- `enterprise_architecture` - Governance & Architecture

**Response Schema**:
```json
[
  {
    "domainRiskId": "dr-123",
    "appId": "app-001",
    "domain": "security",
    "derivedFrom": "security_rating",
    "arb": "security",
    "title": "Security Domain Risks",
    "description": "Aggregated security risks derived from security_rating assessment.",
    "totalItems": 5,
    "openItems": 3,
    "highPriorityItems": 2,
    "overallPriority": "HIGH",
    "overallSeverity": "high",
    "priorityScore": 85,
    "status": "UNDER_ARB_REVIEW",
    "assignedArb": "security",
    "assignedAt": "2025-10-12T10:00:00Z",
    "openedAt": "2025-10-10T09:00:00Z",
    "lastItemAddedAt": "2025-10-11T14:30:00Z",
    "createdAt": "2025-10-10T09:00:00Z",
    "updatedAt": "2025-10-11T14:30:00Z"
  }
]
```

**Frontend Display**:
- **Domain Risk Cards**: One card per app+domain
- **Priority Heatmap**: Color-coded by priorityScore
- **Aggregate Metrics**: Show totalItems, openItems, highPriorityItems
- **Sort Options**: By priority, app, last updated
- **Status Filter Chips**: Quick filters for status values

---

### 3.2 Get ARB Dashboard Summary

**Purpose**: Get aggregate statistics across all domains for ARB dashboard KPIs.

**Endpoint**: `GET /api/v1/domain-risks/arb/{arbName}/summary`

**Query Parameters**:
- `status` (optional): Filter by status

**Example Request**:
```bash
GET /api/v1/domain-risks/arb/security/summary
```

**Response Schema**:
```json
[
  {
    "domain": "security",
    "count": 10,
    "totalOpenItems": 25,
    "avgPriorityScore": 72.5
  },
  {
    "domain": "confidentiality",
    "count": 5,
    "totalOpenItems": 12,
    "avgPriorityScore": 68.0
  }
]
```

**Frontend Display**:
- **KPI Tiles**: Large cards with key metrics
- **Pie Chart**: Distribution by domain
- **Bar Chart**: Open items per domain
- **Trend Lines**: Priority score over time

**Recommended KPIs**:
1. **Total Open Risks**: Sum of `count` across domains
2. **High Priority Risks**: Count where `avgPriorityScore > 70`
3. **Average Resolution Time**: Calculate from timestamps
4. **Workload Distribution**: Items per domain

---

### 3.3 Get Comprehensive ARB Dashboard

**Purpose**: Get ALL metrics needed for a complete ARB dashboard in a single API call. This endpoint aggregates data from multiple sources for optimal frontend performance.

**Endpoint**: `GET /api/v1/domain-risks/arb/{arbName}/dashboard`

**Query Parameters**:
- `status` (optional): Filter by status (default: active statuses)

**Example Request**:
```bash
GET /api/v1/domain-risks/arb/security/dashboard
```

**Response Schema**:
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
  "domains": [
    {
      "domain": "security",
      "riskCount": 10,
      "openItems": 126,
      "criticalItems": 15,
      "avgPriorityScore": 55.0,
      "topPriorityStatus": "PENDING_ARB_REVIEW"
    }
  ],
  "topApplications": [
    {
      "appId": "APM100001",
      "appName": null,
      "domainRiskCount": 1,
      "totalOpenItems": 14,
      "highestPriorityScore": 55,
      "criticalDomain": "security"
    }
  ],
  "statusDistribution": {
    "PENDING_ARB_REVIEW": 10,
    "IN_PROGRESS": 0,
    "RESOLVED": 0
  },
  "priorityDistribution": {
    "critical": 0,
    "high": 0,
    "medium": 10,
    "low": 0
  },
  "recentActivity": {
    "newRisksLast7Days": 0,
    "newRisksLast30Days": 10,
    "resolvedLast7Days": 0,
    "resolvedLast30Days": 0
  }
}
```

**Frontend Benefits**:
- **Single API call** instead of multiple requests
- Complete data for dashboard visualization
- Optimized queries (calculated server-side)
- Ready-to-display metrics

**Recommended UI Layout**:
```
┌────────────────────────────────────────────────────┐
│  Security ARB Dashboard                            │
├────────────────────────────────────────────────────┤
│  [Total: 10] [Open: 126] [Critical: 2] [Avg: 55]  │  ← overview
├────────────────────────────────────────────────────┤
│  Domain Breakdown:                                 │
│  ┌──────────────┐ ┌──────────────┐                │
│  │ Security (10)│ │ Confid... (5)│                │  ← domains
│  │ Score: 55    │ │ Score: 68    │                │
│  └──────────────┘ └──────────────┘                │
├────────────────────────────────────────────────────┤
│  Top Applications:                                 │
│  1. APM100001 (14 items, score: 55)               │  ← topApplications
│  2. APM100002 (13 items, score: 55)               │
├────────────────────────────────────────────────────┤
│  Status: [Pending: 10] [In Progress: 0]           │  ← statusDistribution
│  Priority: [Critical: 0] [High: 0] [Medium: 10]   │  ← priorityDistribution
├────────────────────────────────────────────────────┤
│  Activity: +0 new (7d) | 0 resolved (7d)          │  ← recentActivity
└────────────────────────────────────────────────────┘
```

---

### 3.4 Get Risk Items for Domain

**Purpose**: Drill down from domain-level to evidence-level risk items.

**Endpoint**: `GET /api/v1/domain-risks/{domainRiskId}/items`

**Example Request**:
```bash
GET /api/v1/domain-risks/dr-123/items
```

**Response Schema**: Array of `RiskItemResponse` objects

**Frontend Display**:
- **Drill-Down Table**: Click domain card → see items
- **Breadcrumb**: "Security ARB > Security Domain > Risk Items"
- **Grouped View**: Group by app, field, or status

---

### 3.4 Get Domain Risks for App

**Purpose**: View all domain risks for a specific application.

**Endpoint**: `GET /api/v1/domain-risks/app/{appId}`

**Example Request**:
```bash
GET /api/v1/domain-risks/app/app-001
```

**Response Schema**: Array of `DomainRiskResponse` objects

**Frontend Use Cases**:
- **App-Centric View**: See all domains for one app
- **Cross-Domain Analysis**: Compare security vs integrity risks
- **App Health Dashboard**: Overall compliance status

---

## 4. Analytics & KPI Endpoints

### 4.1 Get Evidence by State (KPI View)

**Purpose**: Get compliance state breakdowns for dashboard metrics.

**Endpoint**: `GET /api/evidence/by-state`

**Query Parameters**:
- `states` (optional): Comma-separated states (default: all)
  - Values: `compliant`, `pending-review`, `missing-evidence`, `risk-blocked`
- `appId` (optional): Filter by app
- `criticality` (optional): Filter by criticality
- `domain` (optional): Filter by domain
- `page`, `size`: Pagination

**Example Request**:
```bash
GET /api/evidence/by-state?states=pending-review,risk-blocked&domain=security
```

**Response Schema**:
```json
{
  "pendingReview": {
    "items": [...],
    "total": 15,
    "page": 1
  },
  "riskBlocked": {
    "items": [...],
    "total": 8,
    "page": 1
  }
}
```

**Frontend Display**:
- **State Donut Chart**: Visual breakdown
- **Drill-Down**: Click slice → see items
- **Trend Widget**: Track changes over time

---

## 5. Priority & Severity Reference

### Priority Scores (0-100 scale)
- **90-100**: CRITICAL (red)
- **70-89**: HIGH (orange)
- **40-69**: MEDIUM (yellow)
- **0-39**: LOW (blue)

### Evidence Status Multipliers (Actual Implementation)
- `missing` / `not_provided`: **2.5x** (highest urgency)
- `non_compliant` / `failed`: **2.3x** (very high urgency)
- `expired`: **2.0x** (high urgency)
- `under_review` / `pending`: **1.5x** (medium urgency)
- `needs_update`: **1.3x** (medium urgency)
- **`approved` / `compliant`: 1.0x** (base priority - compliant)
- `waived` / `exempted`: **0.5x** (lower priority - accepted risk)

**Priority Score Formula**:
```
Score = Base Priority × Evidence Multiplier
Base: CRITICAL=40, HIGH=30, MEDIUM=20, LOW=10
Result: 0-100 (capped at 100)

Example: CRITICAL (40) × missing (2.5) = 100
Example: HIGH (30) × approved (1.0) = 30
```

### Domain Risk Status Flow
```
PENDING_ARB_REVIEW
  ↓
UNDER_ARB_REVIEW
  ↓
AWAITING_REMEDIATION
  ↓
IN_PROGRESS
  ↓
RESOLVED (auto-transitions when all items closed)
```

---

## 6. Sample Dashboard Queries

### Query 1: SME Workbench - Pending Reviews

**Purpose**: Get all pending evidence for logged-in SME

```javascript
GET /api/evidence/pending-sme-review?assignedSme=${smeEmail}
```

**Display**: List with filters, sort by priority/date

---

### Query 2: High-Priority Risks Needing Attention

**Purpose**: Get critical open risks for SME's domain

```javascript
GET /api/v1/risk-items/app/${appId}/status/OPEN
// Filter client-side: priorityScore >= 90
```

**Display**: Alert cards at top of dashboard

---

### Query 3: My ARB's Domain Risks

**Purpose**: Strategic overview of all domains

```javascript
GET /api/v1/domain-risks/arb/security?status=PENDING_ARB_REVIEW,UNDER_ARB_REVIEW
```

**Display**: Domain risk cards with priority scores

---

### Query 3b: Comprehensive ARB Dashboard (Recommended)

**Purpose**: Get ALL dashboard data in single request

```javascript
GET /api/v1/domain-risks/arb/security/dashboard
```

**Display**: Complete dashboard with overview, domains, apps, status, priority, activity

---

### Query 4: Evidence Impact Analysis

**Purpose**: Show before/after when approving evidence

```javascript
// Before approval
GET /api/v1/risk-items/evidence/${evidenceId}
// Note: priorityScore = 100, evidenceStatus = "submitted"

// Approve
POST /api/evidence/${evidenceId}/review?profileFieldId=${profileFieldId}
Body: {"action": "approve", ...}

// After approval (refresh)
GET /api/v1/risk-items/evidence/${evidenceId}
// Note: priorityScore = 50, evidenceStatus = "approved"
```

**Display**: Side-by-side comparison showing score reduction

---

## 7. Recommended Dashboard Components

### Component 1: Pending Reviews Queue

**Data Source**: `GET /api/evidence/pending-sme-review`

**UI Elements**:
- Table/List with columns: App, Field, Submitted By, Date, Priority, Actions
- Quick Actions: Approve, Reject, View Details
- Bulk Select: Approve/reject multiple items
- Filters: Priority, Date Range, App, Field

---

### Component 2: Priority Risk Items

**Data Source**: `GET /api/v1/risk-items/app/{appId}/status/OPEN`

**UI Elements**:
- Card grid sorted by priorityScore
- Priority badge (CRITICAL/HIGH/MEDIUM/LOW)
- Evidence status indicator
- Click → View details + comments

---

### Component 3: Domain Risk Overview

**Data Source**: `GET /api/v1/domain-risks/arb/{arbName}/summary`

**UI Elements**:
- KPI tiles (total risks, avg score, open items)
- Donut chart by domain
- Trend lines (priority over time)
- Click domain → Drill down to items

---

### Component 4: Activity Timeline

**Data Source**: `GET /api/v1/risk-items/{id}/comments`

**UI Elements**:
- Chronological feed of comments, status changes
- Avatar + timestamp for each entry
- Rich text rendering (markdown support)
- Add comment form at bottom

---

## 8. Error Handling

### Common Error Responses

**404 Not Found**:
```json
{
  "timestamp": "2025-10-12T15:30:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Risk item not found: item-999",
  "path": "/api/v1/risk-items/item-999"
}
```

**400 Bad Request**:
```json
{
  "timestamp": "2025-10-12T15:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid action: must be 'approve' or 'reject'"
}
```

**Frontend Handling**:
- Show user-friendly error messages
- Log errors for debugging
- Retry failed requests with exponential backoff
- Offline mode: Cache data, sync when online

---

## 9. Performance Considerations

### Pagination
- Default page size: 20-50 items
- Use cursor-based pagination for large datasets
- Implement infinite scroll or "Load More" button

### Caching
- Cache ARB summary for 5 minutes
- Cache domain risks for 2 minutes
- Invalidate cache on user actions (approve/reject)

### Polling
- Poll pending reviews every 30 seconds
- Poll risk items every 60 seconds
- Use WebSocket for real-time updates (future enhancement)

### Loading States
- Show skeleton screens while loading
- Progressive loading: Load critical data first
- Optimistic updates: Update UI immediately, rollback on error

---

## 10. Authentication & Authorization

**Headers Required**:
```
Authorization: Bearer <jwt-token>
X-User-Email: sme@example.com
```

**Role-Based Access**:
- SMEs can only see evidence/risks for their assigned domain
- ARB parameter should match user's assigned ARB (enforce server-side)
- Internal comments visible only to ARB members

---

## 11. Testing the API

**Import Insomnia Collection**:
```bash
File: insomnia-risk-aggregation-api.json
```

**Test Sequence**:
1. Get profile → Find profileFieldId
2. Create evidence
3. Attach to field (triggers risk creation)
4. Get pending reviews (should show new evidence)
5. Approve evidence (priority drops)
6. Get risk item (verify score reduced)
7. Get domain risk (verify recalculation)

**Environment Variables**:
```
base_url: http://localhost:8181
app_id: APM100001
sme_email: sme@example.com
arb_name: security
```

**Available ARB Names**:
- `security` - Security domain
- `data` - Confidentiality & Integrity
- `operations` - Availability & Resilience
- `enterprise_architecture` - Governance

---

## 12. Next Steps

1. ✅ Review this specification with frontend team
2. 📊 Design mockups based on recommended components
3. 🔄 Implement API integration layer
4. 🧪 Test with sample data using Insomnia
5. 🚀 Build dashboard components iteratively
6. 📈 Add analytics/charts for KPIs

---

## Questions?

- **API Issues**: Check logs at `/logs` endpoint (if available)
- **Data Questions**: Reference `RISK_AGGREGATION_API.md` for detailed schemas
- **Testing**: Use `TESTING_GUIDE.md` for end-to-end scenarios
- **Insomnia Collection**: Import `insomnia-risk-aggregation-api.json`

---

**Document Version**: 1.0
**Last Updated**: 2025-10-12
**Author**: Control Plane Team
