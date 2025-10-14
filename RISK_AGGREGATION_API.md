# Risk Aggregation API Documentation

## ⚠️ IMPORTANT - Corrected Information (2025-10-12)

Following frontend team feedback, this document has been **corrected** with:
- ✅ **Base URL**: `http://localhost:8181` (NOT 8080)
- ✅ **ARB Names**: Short format (`security`, `data`, `operations`, `enterprise_architecture`) - NOT `_arb` suffix
- ✅ **New Endpoints**: Added comprehensive dashboard, pending-sme-review, and search endpoints

---

## Overview

The Risk Aggregation API provides endpoints for managing domain-level risk aggregations and evidence-level risk items. It supports two primary personas:

- **ARB/SME View**: Domain-level aggregations for Architecture Review Boards
- **PO/Developer View**: Evidence-level risk items for Product Owners

## Architecture

```
Evidence → RiskItem → DomainRisk → ARB
                ↓           ↓
            PO View    ARB View
```

### Key Concepts

1. **Risk Item**: Individual evidence-level risk
   - One per evidence submission that requires review
   - Contains priority score (0-100), severity, status
   - Linked to triggering evidence and field

2. **Domain Risk**: Aggregation of risk items by domain
   - One per app+domain combination (e.g., app-123/security)
   - Auto-calculates: total items, open items, priority score
   - Routes to appropriate ARB based on domain

3. **Priority Scoring**:
   - Base score from priority enum (CRITICAL=40, HIGH=30, etc.)
   - Multiplied by evidence status (missing=2.5x, non_compliant=2.3x)
   - Domain score = max item score + bonuses
   - Scale: 0-100, higher = more urgent

## Base URL

```
http://localhost:8181
```

**Important**: This service runs on port **8181** (NOT 8080). All endpoints use this base URL.

## Authentication

*To be implemented - currently no authentication required for testing*

---

## Domain Risk Endpoints (ARB/SME View)

### 1. Get Domain Risks for ARB

Get all domain risks assigned to a specific ARB, with status filtering.

**Endpoint**: `GET /api/v1/domain-risks/arb/{arbName}`

**Parameters**:
- `arbName` (path): ARB identifier - **Use short format**: `security`, `data`, `operations`, `enterprise_architecture`
- `status` (query, optional): Comma-separated list of statuses
  - Default: `PENDING_ARB_REVIEW,UNDER_ARB_REVIEW,AWAITING_REMEDIATION,IN_PROGRESS`
  - Valid values: `PENDING_ARB_REVIEW`, `UNDER_ARB_REVIEW`, `AWAITING_REMEDIATION`, `IN_PROGRESS`, `RESOLVED`, `WAIVED`, `CLOSED`

**Important - ARB Naming**:
- ✅ **Correct**: `security`, `data`, `operations`, `enterprise_architecture`
- ❌ **Incorrect**: `security_arb`, `data_arb`, `integrity_arb`

**Example Request**:
```bash
GET /api/v1/domain-risks/arb/security?status=PENDING_ARB_REVIEW,UNDER_ARB_REVIEW
```

**Example Response**:
```json
[
  {
    "domainRiskId": "dr-uuid-123",
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
    "closedAt": null,
    "lastItemAddedAt": "2025-10-11T14:30:00Z",
    "createdAt": "2025-10-10T09:00:00Z",
    "updatedAt": "2025-10-11T14:30:00Z"
  }
]
```

---

### 2. Get ARB Dashboard Summary

Get aggregate statistics across domains for ARB dashboard.

**Endpoint**: `GET /api/v1/domain-risks/arb/{arbName}/summary`

**Parameters**:
- `arbName` (path): ARB identifier
- `status` (query, optional): Comma-separated list of statuses

**Example Request**:
```bash
GET /api/v1/domain-risks/arb/security/summary
```

**Example Response**:
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

---

### 3. Get ARB Dashboard (Comprehensive)

Get complete dashboard metrics for ARB in a single API call. **Recommended for frontend** - returns all data needed for dashboard visualization.

**Endpoint**: `GET /api/v1/domain-risks/arb/{arbName}/dashboard`

**Parameters**:
- `arbName` (path): ARB identifier (short format)
- `status` (query, optional): Comma-separated list of statuses

**Example Request**:
```bash
GET /api/v1/domain-risks/arb/security/dashboard
```

**Example Response**:
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

**Why Use This Endpoint?**
- Single API call returns all dashboard data
- More efficient than `/summary` (which only returns domain breakdown)
- Includes: overview, domains, top apps, distributions, recent activity

---

### 4. Get Domain Risk by ID

Get details of a specific domain risk.

**Endpoint**: `GET /api/v1/domain-risks/{domainRiskId}`

**Example Request**:
```bash
GET /api/v1/domain-risks/dr-uuid-123
```

**Example Response**: Same as domain risk object above

---

### 5. Get Risk Items for Domain

Get all risk items belonging to a domain risk (drill-down from domain to evidence level).

**Endpoint**: `GET /api/v1/domain-risks/{domainRiskId}/items`

**Example Request**:
```bash
GET /api/v1/domain-risks/dr-uuid-123/items
```

**Example Response**: Array of RiskItemResponse objects (see Risk Item endpoints below)

---

### 6. Get Domain Risks for App

Get all domain risks for a specific application.

**Endpoint**: `GET /api/v1/domain-risks/app/{appId}`

**Example Request**:
```bash
GET /api/v1/domain-risks/app/app-001
```

**Example Response**: Array of DomainRiskResponse objects

---

### 7. Reassign Domain Risk

Reassign a domain risk to a different ARB for workload balancing or expertise matching.

**Endpoint**: `PATCH /api/v1/domain-risks/{domainRiskId}/assign`

**Request Body**:
```json
{
  "assignedArb": "data",
  "assignedBy": "admin_user",
  "assignmentReason": "Workload balancing - security team overloaded"
}
```

**Fields**:
- `assignedArb` (required): New ARB identifier (use short format: `security`, `data`, `operations`, `enterprise_architecture`)
- `assignedBy` (required): User performing the reassignment
- `assignmentReason` (optional): Reason for reassignment

**Example Request**:
```bash
PATCH /api/v1/domain-risks/dr-uuid-123/assign
Content-Type: application/json

{
  "assignedArb": "data",
  "assignedBy": "admin_user",
  "assignmentReason": "Reassigning to data team for better expertise match"
}
```

**Example Response**: Updated DomainRiskResponse object with new `assignedArb` and `assignedAt`

---

## Evidence Lifecycle Endpoints

These endpoints manage the evidence submission → risk creation → approval workflow that triggers automatic risk item creation.

### 1. Get Pending SME Review Evidence

Get all evidence items pending SME review for a specific SME.

**Endpoint**: `GET /api/evidence/pending-sme-review`

**Query Parameters**:
- `assignedSme` (required): SME email or identifier

**Example Request**:
```bash
GET /api/evidence/pending-sme-review?assignedSme=sme@example.com
```

**Example Response**:
```json
[
  {
    "evidenceId": "evidence-123",
    "appId": "APM100001",
    "fieldKey": "encryption_at_rest",
    "profileFieldId": "pf-789",
    "linkStatus": "PENDING_SME_REVIEW",
    "linkedAt": "2025-10-12T10:00:00Z",
    "linkedBy": "developer@example.com",
    "documentId": "doc-456",
    "documentTitle": "Encryption Policy v2",
    "documentUri": "https://gitlab.com/docs/encryption-policy"
  }
]
```

---

### 2. Search Evidence (Advanced)

Search evidence with multiple filter criteria.

**Endpoint**: `GET /api/evidence/search`

**Query Parameters**:
- `linkStatus` (optional): Filter by link status (PENDING_SME_REVIEW, APPROVED, REJECTED, etc.)
- `appId` (optional): Filter by application
- `fieldKey` (optional): Filter by field key
- `criticality` (optional): Filter by criticality
- `domain` (optional): Filter by domain
- `assignedPo` (optional): Filter by Product Owner
- `assignedSme` (optional): Filter by SME
- `enhanced` (optional): Return enhanced response (default: true)
- `page` (optional): Page number (default: 0)
- `size` (optional): Page size (default: 100)

**Example Request**:
```bash
GET /api/evidence/search?linkStatus=PENDING_SME_REVIEW&appId=APM100001&fieldKey=encryption_at_rest
```

**Example Response**: Array of EnhancedEvidenceSummary objects (same format as pending-sme-review response)

---

### 3. Evidence Approval/Rejection (SME Workflow)

Approve or reject evidence submitted for high-criticality fields requiring SME review.

**Endpoint**: `POST /api/evidence/{evidenceId}/review`

**Query Parameters**:
- `profileFieldId` (required): Profile field ID the evidence is linked to

**Request Body**:
```json
{
  "action": "approve",
  "reviewerId": "sme@example.com",
  "reviewComment": "Optional review notes"
}
```

**Fields**:
- `action` (required): `"approve"` or `"reject"`
- `reviewerId` (required): SME identifier performing the review
- `reviewComment` (optional): Review notes or feedback

**Example Request (Approval)**:
```bash
POST /api/evidence/evidence-789/review?profileFieldId=pf-test-001
Content-Type: application/json

{
  "action": "approve",
  "reviewerId": "sme@example.com",
  "reviewComment": "Encryption policy meets all security requirements."
}
```

**Example Request (Rejection)**:
```bash
POST /api/evidence/evidence-789/review?profileFieldId=pf-test-001
Content-Type: application/json

{
  "action": "reject",
  "reviewerId": "sme@example.com",
  "reviewComment": "Documentation insufficient. Please provide more detail on key management."
}
```

**Example Response**:
```json
{
  "evidenceId": "evidence-789",
  "profileFieldId": "pf-test-001",
  "linkStatus": "APPROVED",
  "linkedBy": "developer@example.com",
  "linkedAt": "2025-10-12T10:00:00Z",
  "reviewedBy": "sme@example.com",
  "reviewedAt": "2025-10-12T14:30:00Z",
  "reviewComment": "Encryption policy meets all security requirements."
}
```

**Status Transitions**:
- **Approve**: `PENDING_SME_REVIEW` → `APPROVED`
- **Reject**: `PENDING_SME_REVIEW` → `REJECTED`

**Side Effects** (Critical!):
1. **Updates evidence field link status** to `APPROVED` or `REJECTED`
2. **Triggers automatic risk recalculation** if a risk item exists for this evidence
3. **Priority score multipliers applied**:
   - `approved`: **0.5x multiplier** (50% score reduction - e.g., 100 → 50)
   - `rejected`: 0.9x multiplier (10% reduction)
   - `submitted` (pending): 1.0x multiplier (full priority)
4. **Domain risk aggregations recalculated** with new priority scores
5. **Timestamps updated**: `updatedAt` on both risk item and domain risk

**Priority Score Impact Example**:
```
Before Approval:
- Risk item: status=OPEN, evidenceStatus="submitted", priorityScore=100
- Domain risk: priorityScore=95, openItems=1

After Approval:
- Risk item: status=OPEN, evidenceStatus="approved", priorityScore=50 ✅
- Domain risk: priorityScore=55, openItems=1 ✅
```

**Status Code**: `200 OK`

**Use Cases**:
- **High-criticality fields** (A1, A2 ratings) require SME approval
- **Security, integrity, confidentiality** domain evidence
- Evidence validation before accepting compliance claims

---

## Risk Item Endpoints (PO View)

### 1. Get Risk Items for App

Get all risk items for an application, prioritized by score (highest priority first).

**Endpoint**: `GET /api/v1/risk-items/app/{appId}`

**Example Request**:
```bash
GET /api/v1/risk-items/app/app-001
```

**Example Response**:
```json
[
  {
    "riskItemId": "item-uuid-456",
    "domainRiskId": "dr-uuid-123",
    "appId": "app-001",
    "fieldKey": "encryption_at_rest",
    "profileFieldId": "pf-789",
    "triggeringEvidenceId": "evidence-001",
    "trackId": null,
    "title": "Compliance risk: encryption_at_rest",
    "description": "Evidence for encryption_at_rest requires review due to A1 rating configuration",
    "priority": "CRITICAL",
    "severity": "critical",
    "priorityScore": 100,
    "evidenceStatus": "missing",
    "status": "OPEN",
    "resolution": null,
    "resolutionComment": null,
    "creationType": "SYSTEM_AUTO_CREATION",
    "raisedBy": "SYSTEM_AUTO_CREATION",
    "openedAt": "2025-10-11T14:30:00Z",
    "resolvedAt": null,
    "policyRequirementSnapshot": {
      "fieldKey": "encryption_at_rest",
      "fieldLabel": "Encryption at Rest",
      "complianceFrameworks": [...]
    },
    "createdAt": "2025-10-11T14:30:00Z",
    "updatedAt": "2025-10-11T14:30:00Z"
  }
]
```

---

### 2. Get Risk Items by Status

Get risk items for an app filtered by status.

**Endpoint**: `GET /api/v1/risk-items/app/{appId}/status/{status}`

**Parameters**:
- `status` (path): One of `OPEN`, `IN_PROGRESS`, `RESOLVED`, `WAIVED`, `CLOSED`

**Example Request**:
```bash
GET /api/v1/risk-items/app/app-001/status/OPEN
```

**Example Response**: Array of RiskItemResponse objects

---

### 3. Get Risk Item by ID

Get details of a specific risk item.

**Endpoint**: `GET /api/v1/risk-items/{riskItemId}`

**Example Request**:
```bash
GET /api/v1/risk-items/item-uuid-456
```

**Example Response**: Single RiskItemResponse object

---

### 4. Update Risk Item Status

Update the status of a risk item. **This triggers automatic recalculation of domain risk aggregations.**

**Endpoint**: `PATCH /api/v1/risk-items/{riskItemId}/status`

**Request Body**:
```json
{
  "status": "RESOLVED",
  "resolution": "REMEDIATED",
  "resolutionComment": "Applied security patches and updated configuration as recommended."
}
```

**Fields**:
- `status` (required): New status - `OPEN`, `IN_PROGRESS`, `RESOLVED`, `WAIVED`, `CLOSED`
- `resolution` (optional): Resolution type - `FIXED`, `REMEDIATED`, `RISK_ACCEPTED`, `VERIFIED`, etc.
- `resolutionComment` (optional): Explanation of resolution

**Example Request**:
```bash
PATCH /api/v1/risk-items/item-uuid-456/status
Content-Type: application/json

{
  "status": "RESOLVED",
  "resolution": "REMEDIATED",
  "resolutionComment": "Applied security patches and updated configuration as recommended."
}
```

**Example Response**: Updated RiskItemResponse object

**Status Transition Examples**:

1. **Resolve a risk**:
```json
{
  "status": "RESOLVED",
  "resolution": "REMEDIATED",
  "resolutionComment": "Issue fixed by implementing encryption at rest using AES-256."
}
```

2. **Waive a risk**:
```json
{
  "status": "WAIVED",
  "resolution": "RISK_ACCEPTED",
  "resolutionComment": "Risk accepted by security team. Compensating controls in place."
}
```

3. **Close a risk**:
```json
{
  "status": "CLOSED",
  "resolution": "VERIFIED",
  "resolutionComment": "Fix verified by ARB. Closing risk item."
}
```

---

### 5. Get Risk Items by Field

Get all risk items for a specific field key (useful for field-specific analysis).

**Endpoint**: `GET /api/v1/risk-items/field/{fieldKey}`

**Example Request**:
```bash
GET /api/v1/risk-items/field/encryption_at_rest
```

**Example Response**: Array of RiskItemResponse objects

---

### 6. Get Risk Items by Evidence

Get all risk items triggered by a specific evidence submission.

**Endpoint**: `GET /api/v1/risk-items/evidence/{evidenceId}`

**Example Request**:
```bash
GET /api/v1/risk-items/evidence/evidence-001
```

**Example Response**: Array of RiskItemResponse objects

---

### 7. Create Risk Item Manually

Create a risk item manually (not triggered by evidence submission). Used by ARB/SME to create risks outside the automatic flow.

**Endpoint**: `POST /api/v1/risk-items`

**Request Body**:
```json
{
  "appId": "app-001",
  "fieldKey": "encryption_at_rest",
  "profileFieldId": "pf-789",
  "title": "Manual risk: Encryption configuration review",
  "description": "Security team identified potential encryption misconfiguration during audit",
  "priority": "HIGH",
  "createdBy": "security_arb_user",
  "evidenceId": null
}
```

**Fields**:
- `appId` (required): Application ID
- `fieldKey` (required): Field key from registry
- `profileFieldId` (optional): Profile field ID if available
- `title` (required): Risk title
- `description` (required): Risk description
- `priority` (required): One of `CRITICAL`, `HIGH`, `MEDIUM`, `LOW`
- `createdBy` (required): User creating the risk
- `evidenceId` (optional): Evidence ID if related to evidence

**Example Request**:
```bash
POST /api/v1/risk-items
Content-Type: application/json

{
  "appId": "app-001",
  "fieldKey": "encryption_at_rest",
  "profileFieldId": "pf-789",
  "title": "Manual risk: Encryption configuration review",
  "description": "Security team identified potential encryption misconfiguration during audit",
  "priority": "HIGH",
  "createdBy": "security_arb_user",
  "evidenceId": null
}
```

**Example Response**:
```json
{
  "riskItemId": "item-uuid-789",
  "domainRiskId": "dr-uuid-123",
  "appId": "app-001",
  "fieldKey": "encryption_at_rest",
  "profileFieldId": "pf-789",
  "triggeringEvidenceId": null,
  "trackId": null,
  "title": "Manual risk: Encryption configuration review",
  "description": "Security team identified potential encryption misconfiguration during audit",
  "priority": "HIGH",
  "severity": "high",
  "priorityScore": 75,
  "evidenceStatus": "approved",
  "status": "OPEN",
  "resolution": null,
  "resolutionComment": null,
  "creationType": "MANUAL_CREATION",
  "raisedBy": "security_arb_user",
  "openedAt": "2025-10-12T16:00:00Z",
  "resolvedAt": null,
  "policyRequirementSnapshot": null,
  "createdAt": "2025-10-12T16:00:00Z",
  "updatedAt": "2025-10-12T16:00:00Z"
}
```

**Status Code**: `201 Created`

---

### 8. Add Comment to Risk Item

Add a comment to a risk item for discussion and collaboration.

**Endpoint**: `POST /api/v1/risk-items/{riskItemId}/comments`

**Request Body**:
```json
{
  "commentType": "REVIEW",
  "commentText": "Reviewed the encryption configuration. Needs to implement AES-256 with proper key management.",
  "commentedBy": "security_arb_user",
  "isInternal": false
}
```

**Fields**:
- `commentType` (required): One of `GENERAL`, `STATUS_CHANGE`, `REVIEW`, `RESOLUTION`
- `commentText` (required): Comment content
- `commentedBy` (required): User adding the comment
- `isInternal` (optional): `true` for internal ARB notes, `false` for PO-visible (default: `false`)

**Example Request**:
```bash
POST /api/v1/risk-items/item-uuid-456/comments
Content-Type: application/json

{
  "commentType": "REVIEW",
  "commentText": "Reviewed the encryption configuration. Needs to implement AES-256 with proper key management.",
  "commentedBy": "security_arb_user",
  "isInternal": false
}
```

**Example Response**:
```json
{
  "commentId": "comment-uuid-111",
  "riskItemId": "item-uuid-456",
  "commentType": "REVIEW",
  "commentText": "Reviewed the encryption configuration. Needs to implement AES-256 with proper key management.",
  "commentedBy": "security_arb_user",
  "commentedAt": "2025-10-12T16:15:00Z",
  "isInternal": false,
  "createdAt": "2025-10-12T16:15:00Z",
  "updatedAt": "2025-10-12T16:15:00Z"
}
```

**Status Code**: `201 Created`

---

### 9. Get Comments for Risk Item

Get all comments for a risk item, with optional filtering for internal comments.

**Endpoint**: `GET /api/v1/risk-items/{riskItemId}/comments`

**Parameters**:
- `includeInternal` (query, optional): Include internal ARB notes (default: `false`)

**Example Request (Public comments only)**:
```bash
GET /api/v1/risk-items/item-uuid-456/comments
```

**Example Request (Include internal comments)**:
```bash
GET /api/v1/risk-items/item-uuid-456/comments?includeInternal=true
```

**Example Response**:
```json
[
  {
    "commentId": "comment-uuid-111",
    "riskItemId": "item-uuid-456",
    "commentType": "REVIEW",
    "commentText": "Reviewed the encryption configuration. Needs to implement AES-256 with proper key management.",
    "commentedBy": "security_arb_user",
    "commentedAt": "2025-10-12T16:15:00Z",
    "isInternal": false,
    "createdAt": "2025-10-12T16:15:00Z",
    "updatedAt": "2025-10-12T16:15:00Z"
  },
  {
    "commentId": "comment-uuid-112",
    "riskItemId": "item-uuid-456",
    "commentType": "GENERAL",
    "commentText": "PO acknowledged. Working on implementation plan.",
    "commentedBy": "product_owner",
    "commentedAt": "2025-10-12T17:00:00Z",
    "isInternal": false,
    "createdAt": "2025-10-12T17:00:00Z",
    "updatedAt": "2025-10-12T17:00:00Z"
  }
]
```

---

## Status Enums

### DomainRiskStatus
- `PENDING_ARB_REVIEW` - Awaiting initial ARB review
- `UNDER_ARB_REVIEW` - Currently being reviewed by ARB
- `AWAITING_REMEDIATION` - ARB reviewed, waiting for fixes
- `IN_PROGRESS` - Remediation in progress
- `RESOLVED` - All items resolved (auto-transitioned)
- `WAIVED` - Risk waived by ARB
- `CLOSED` - Risk closed

### RiskItemStatus
- `OPEN` - New risk item, needs attention
- `IN_PROGRESS` - Being worked on
- `RESOLVED` - Issue fixed/remediated
- `WAIVED` - Risk accepted/waived
- `CLOSED` - Verified and closed

### RiskPriority
- `CRITICAL` - Score 90-100
- `HIGH` - Score 70-89
- `MEDIUM` - Score 40-69
- `LOW` - Score 0-39

### RiskCreationType
- `SYSTEM_AUTO_CREATION` - Automatically created by evidence submission
- `MANUAL_CREATION` - Manually created by ARB/SME
- `MANUAL_SME_INITIATED` - Legacy manual creation
- `AUTO` - Legacy auto creation

### RiskCommentType
- `GENERAL` - General comment or discussion
- `STATUS_CHANGE` - Comment related to status change
- `REVIEW` - ARB/SME review comment
- `RESOLUTION` - Resolution-related comment

---

## Testing with Insomnia

### Import Collection

1. Open Insomnia
2. Click "Create" → "Import from File"
3. Select `insomnia-risk-aggregation-api.json`
4. Collection will be imported with all endpoints organized into folders

### Environment Variables

The collection includes environment variables you can customize:

- `base_url`: API base URL (default: `http://localhost:8181`)
- `app_id`: Test application ID
- `arb_name`: Test ARB name (e.g., `security`, `data`, `operations`)
- `domain_risk_id`: Domain risk ID for testing
- `risk_item_id`: Risk item ID for testing
- `field_key`: Field key for testing (e.g., `encryption_at_rest`)
- `evidence_id`: Evidence ID for testing
- `profile_field_id`: Profile field ID for testing
- `sme_email`: SME email for reviews
- `developer_email`: Developer email for submissions

### Complete Lifecycle Testing Flow

**NEW: Evidence Lifecycle folder** - Test the full auto-creation workflow:

1. **Create evidence** → Capture `evidence_id`
2. **Attach to field** ⚠️ **TRIGGERS AUTO-CREATION** → Capture `risk_item_id`, `domain_risk_id`
3. **Verify risk created** → Check `evidenceStatus="submitted"`, `priorityScore=100`
4. **SME approves** → Priority score drops to ~50
5. **Verify recalculation** → Check `evidenceStatus="approved"`, `priorityScore=50`
6. **Verify domain updated** → Domain risk aggregations recalculated
7. **Resolve risk** → Mark as resolved

### Typical Testing Flow (Risk Management)

1. **Check ARB workbench**:
   - Run: "Get Domain Risks for ARB"
   - Verify domain-level aggregations

2. **View ARB dashboard**:
   - Run: "Get ARB Dashboard Summary"
   - Check aggregate statistics

3. **Drill down to items**:
   - Copy a `domainRiskId` from step 1
   - Update environment variable
   - Run: "Get Risk Items for Domain"

4. **PO workbench**:
   - Run: "Get Risk Items for App"
   - See all items prioritized by score

5. **Update risk status**:
   - Copy a `riskItemId`
   - Update environment variable
   - Run: "Example: Resolve Risk Item"
   - Observe domain risk recalculation

---

## Error Responses

### 404 Not Found
```json
{
  "timestamp": "2025-10-12T15:30:00Z",
  "status": 404,
  "error": "Not Found",
  "path": "/api/v1/domain-risks/invalid-id"
}
```

### 400 Bad Request
```json
{
  "timestamp": "2025-10-12T15:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid status value"
}
```

---

## Additional Notes

### Aggregation Recalculation

When a risk item status is updated via the PATCH endpoint, the following happens automatically:

1. Risk item is updated in database
2. Domain risk aggregations are recalculated:
   - `totalItems`, `openItems`, `highPriorityItems`
   - `priorityScore` (includes bonuses for volume and high-priority items)
   - `overallPriority`, `overallSeverity`
3. Domain risk status may auto-transition:
   - `RESOLVED` when all items are closed
   - `IN_PROGRESS` when new items are added to resolved risk

### Performance Considerations

- All list endpoints return data sorted by priority/relevance
- Use status filters to reduce payload size
- Domain risks are cached at the service layer
- Indexes are in place for all common query patterns

### Future Enhancements

- **Risk History/Audit Trail**: Track full history of status changes and modifications
- **SLA Tracking**: Priority-based deadlines and breach alerts
- **Bulk Operations**: Batch status updates and assignments
- **Notifications**: Real-time alerts for risk assignments and updates
- **Advanced Analytics**: Risk velocity, time-to-resolution metrics
- Pagination for large result sets
- WebSocket notifications for real-time updates
- Export to CSV/Excel
- Advanced filtering and sorting options

### Recently Added (Phase 7)

- **Manual Risk Creation**: ARB/SME can create risks outside automatic flow
- **Risk Comments**: Discussion threading with internal/public visibility
- **Risk Reassignment**: Workload balancing between ARBs
- **SME Evidence Approval**: New `POST /api/evidence/{id}/review` endpoint for evidence approval/rejection workflow
- **Evidence Status Multipliers**: Priority scores now adjust automatically based on evidence status (approved=0.5x, rejected=0.9x, submitted=1.0x)
- **Complete Lifecycle Testing**: New Insomnia folder with 7 requests covering evidence → risk → approval → resolution flow
