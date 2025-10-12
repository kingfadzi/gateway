# Frontend Handoff - ARB/SME Dashboard

**Date:** 2025-10-12
**Backend Service:** Control Plane Gateway (lct_data_v2)
**Base URL:** `http://localhost:8181`
**Status:** ✅ Ready for Development

---

## Overview

This document contains everything the frontend team needs to build the ARB (Architecture Review Board) / SME (Subject Matter Expert) dashboard for risk management.

### What Is This Dashboard?

ARB members (Security, Data, Operations, Enterprise Architecture) need to:
1. View domain-level risk aggregations across all applications
2. Monitor evidence submissions requiring review
3. Drill down from strategic domain view to tactical evidence details
4. Track resolution progress and prioritize work

---

## Quick Start

### 1. Available ARBs (Use These Exact Names)

| ARB Name | Domains Covered | Example Apps |
|----------|-----------------|--------------|
| `security` | Security | Encryption, authentication, access control |
| `data` | Confidentiality, Integrity | Data protection, audit logs |
| `operations` | Availability, Resilience | Uptime, disaster recovery |
| `enterprise_architecture` | Governance | Architecture compliance |

⚠️ **Important**: ARB names are **short format** (`security` NOT `security_arb`)

---

### 2. Primary API Endpoint (Start Here)

**Get Complete Dashboard (Single API Call)**:
```http
GET http://localhost:8181/api/v1/domain-risks/arb/{arbName}/dashboard
```

**Example**:
```bash
curl http://localhost:8181/api/v1/domain-risks/arb/security/dashboard
```

**Response** (Complete dashboard data):
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

---

## Dashboard Layout Recommendation

```
┌──────────────────────────────────────────────────────────────┐
│  Security ARB Dashboard                                      │
├──────────────────────────────────────────────────────────────┤
│  Overview Metrics                                            │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐       │
│  │ Total: 10│ │ Open: 126│ │Crit: 2   │ │ Avg: 55  │       │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘       │
├──────────────────────────────────────────────────────────────┤
│  Domain Breakdown                                            │
│  ┌──────────────────────┐ ┌──────────────────────┐          │
│  │ Security             │ │ Confidentiality      │          │
│  │ 10 risks, 126 items  │ │ 5 risks, 45 items    │          │
│  │ Priority: 55 (MED)   │ │ Priority: 68 (MED)   │          │
│  └──────────────────────┘ └──────────────────────┘          │
├──────────────────────────────────────────────────────────────┤
│  Top Applications (by risk)                                  │
│  1. APM100001 - 14 open items (Score: 55) [security]        │
│  2. APM100002 - 13 open items (Score: 55) [security]        │
│  3. APM100003 - 14 open items (Score: 55) [security]        │
├──────────────────────────────────────────────────────────────┤
│  Status:    [●●●●●●●●●● Pending] [○ In Progress]            │
│  Priority:  [○ Critical] [○ High] [●●●●●●●●●● Medium]        │
├──────────────────────────────────────────────────────────────┤
│  Recent Activity: +0 new this week | 0 resolved this week   │
└──────────────────────────────────────────────────────────────┘
```

---

## Complete API Reference

### Core Endpoints

| Endpoint | Purpose | When to Use |
|----------|---------|-------------|
| `GET /api/v1/domain-risks/arb/{arbName}/dashboard` | Complete dashboard | **Start here** - Single call for full dashboard |
| `GET /api/v1/domain-risks/arb/{arbName}` | List domain risks | Detailed domain risk cards |
| `GET /api/v1/domain-risks/arb/{arbName}/summary` | Summary statistics | Simple KPIs only |
| `GET /api/v1/domain-risks/{domainRiskId}/items` | Drill down to items | Click on domain → see evidence-level risks |
| `GET /api/v1/risk-items/app/{appId}` | App-specific items | Application detail view |

### Detailed Endpoint Specs

#### 1. Get Domain Risks List
```http
GET /api/v1/domain-risks/arb/{arbName}?status=PENDING_ARB_REVIEW,UNDER_ARB_REVIEW
```

**Use Case**: Show cards for each domain risk

**Response**:
```json
[
  {
    "domainRiskId": "dr-123",
    "appId": "APM100001",
    "domain": "security",
    "totalItems": 14,
    "openItems": 14,
    "highPriorityItems": 0,
    "priorityScore": 55,
    "status": "PENDING_ARB_REVIEW",
    "openedAt": "2025-10-12T19:47:53Z"
  }
]
```

**UI**: Domain risk cards sorted by priorityScore DESC

---

#### 2. Drill Down to Risk Items
```http
GET /api/v1/domain-risks/{domainRiskId}/items
```

**Use Case**: User clicks domain risk card → show evidence-level details

**Response**:
```json
[
  {
    "riskItemId": "item-456",
    "domainRiskId": "dr-123",
    "appId": "APM100001",
    "fieldKey": "encryption_at_rest",
    "title": "Compliance risk: encryption_at_rest",
    "priority": "MEDIUM",
    "priorityScore": 50,
    "evidenceStatus": "missing",
    "status": "OPEN",
    "openedAt": "2025-10-11T14:30:00Z"
  }
]
```

**UI**: Table/list with field, priority, status, actions

---

## Priority Scoring System

### Score Ranges (0-100 scale)

| Range | Priority | Color | Badge |
|-------|----------|-------|-------|
| 90-100 | CRITICAL | Red | 🔴 |
| 70-89 | HIGH | Orange | 🟠 |
| 40-69 | MEDIUM | Yellow | 🟡 |
| 0-39 | LOW | Blue | 🔵 |

### How Scores Are Calculated

```
Item Score = Base Priority × Evidence Multiplier
  Base: CRITICAL=40, HIGH=30, MEDIUM=20, LOW=10
  Multiplier: missing=2.5x, expired=2.0x, approved=1.0x, waived=0.5x

Domain Score = Max(Item Scores) + Bonuses
  High priority bonus: up to +10
  Volume bonus: up to +5
```

**Examples**:
- CRITICAL priority + missing evidence: 40 × 2.5 = **100** 🔴
- HIGH priority + missing evidence: 30 × 2.5 = **75** 🟠
- MEDIUM priority + approved evidence: 20 × 1.0 = **20** 🔵

---

## Status Workflows

### Domain Risk Status Flow
```
PENDING_ARB_REVIEW
  ↓
UNDER_ARB_REVIEW
  ↓
IN_PROGRESS
  ↓
RESOLVED (auto-transitions when all items closed)
```

### Risk Item Status Flow
```
OPEN
  ↓
IN_PROGRESS
  ↓
RESOLVED / WAIVED / CLOSED
```

---

## UI Component Specifications

### Component 1: Overview Metrics (KPI Tiles)

**Data Source**: `dashboard.overview`

**Metrics**:
- Total Domain Risks
- Total Open Items
- Critical Count (score >= 90)
- Average Priority Score
- Needs Immediate Attention (score >= 70)

**UI**: Large number tiles, color-coded by urgency

---

### Component 2: Domain Breakdown Cards

**Data Source**: `dashboard.domains`

**Card Layout**:
```
┌────────────────────────┐
│ Security               │
│ 10 risks, 126 items    │
│ Priority: 55 (MEDIUM)  │
│ Status: Pending Review │
└────────────────────────┘
```

**Sorting**: By `avgPriorityScore` DESC

**Click Action**: Navigate to domain detail → show risk items

---

### Component 3: Top Applications List

**Data Source**: `dashboard.topApplications`

**Display**: Table or list, limited to top 10

**Columns**:
- App ID
- Domain Risk Count
- Total Open Items
- Highest Priority Score (with color)
- Critical Domain

---

### Component 4: Status & Priority Distribution

**Data Source**:
- `dashboard.statusDistribution`
- `dashboard.priorityDistribution`

**Visualization**: Horizontal bar charts or donut charts

**Example**:
- Status: Pending (10), In Progress (0), Resolved (0)
- Priority: Critical (0), High (0), Medium (10), Low (0)

---

### Component 5: Recent Activity

**Data Source**: `dashboard.recentActivity`

**Display**: Timeline or activity feed

**Metrics**:
- New risks: Last 7 days, Last 30 days
- Resolved: Last 7 days, Last 30 days

**Example Text**: "+5 new risks this week | 3 resolved this week"

---

## Drill-Down Flow (User Journey)

```
1. Dashboard (GET /arb/security/dashboard)
   ↓ click "Security Domain"

2. Domain Risks List (GET /arb/security)
   ↓ click domain risk card

3. Risk Items Detail (GET /domain-risks/{id}/items)
   ↓ click risk item

4. Risk Item Detail (GET /risk-items/{itemId})
   ↓ view evidence, comments, history
```

---

## Sample Frontend Code (React/TypeScript)

### Fetch Dashboard Data

```typescript
interface ArbDashboard {
  arbName: string;
  overview: {
    totalDomainRisks: number;
    totalOpenItems: number;
    criticalCount: number;
    highCount: number;
    averagePriorityScore: number;
    needsImmediateAttention: number;
  };
  domains: Array<{
    domain: string;
    riskCount: number;
    openItems: number;
    criticalItems: number;
    avgPriorityScore: number;
    topPriorityStatus: string;
  }>;
  topApplications: Array<{
    appId: string;
    appName: string | null;
    domainRiskCount: number;
    totalOpenItems: number;
    highestPriorityScore: number;
    criticalDomain: string;
  }>;
  statusDistribution: Record<string, number>;
  priorityDistribution: {
    critical: number;
    high: number;
    medium: number;
    low: number;
  };
  recentActivity: {
    newRisksLast7Days: number;
    newRisksLast30Days: number;
    resolvedLast7Days: number;
    resolvedLast30Days: number;
  };
}

async function fetchDashboard(arbName: string): Promise<ArbDashboard> {
  const response = await fetch(
    `http://localhost:8181/api/v1/domain-risks/arb/${arbName}/dashboard`
  );
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  return response.json();
}

// Usage
const dashboard = await fetchDashboard('security');
console.log(`Total risks: ${dashboard.overview.totalDomainRisks}`);
console.log(`Needs attention: ${dashboard.overview.needsImmediateAttention}`);
```

### Priority Badge Component

```typescript
function getPriorityColor(score: number): string {
  if (score >= 90) return 'red';
  if (score >= 70) return 'orange';
  if (score >= 40) return 'yellow';
  return 'blue';
}

function getPriorityLabel(score: number): string {
  if (score >= 90) return 'CRITICAL';
  if (score >= 70) return 'HIGH';
  if (score >= 40) return 'MEDIUM';
  return 'LOW';
}

function PriorityBadge({ score }: { score: number }) {
  return (
    <span
      className={`badge badge-${getPriorityColor(score)}`}
      title={`Score: ${score}/100`}
    >
      {getPriorityLabel(score)}
    </span>
  );
}
```

---

## Testing the API

### Using cURL

```bash
# Test dashboard endpoint
curl http://localhost:8181/api/v1/domain-risks/arb/security/dashboard

# Test with jq for pretty output
curl -s http://localhost:8181/api/v1/domain-risks/arb/security/dashboard | jq .

# Test other ARBs
curl http://localhost:8181/api/v1/domain-risks/arb/data/dashboard
curl http://localhost:8181/api/v1/domain-risks/arb/operations/dashboard
curl http://localhost:8181/api/v1/domain-risks/arb/enterprise_architecture/dashboard
```

### Using Insomnia/Postman

Import the collection: `insomnia-risk-aggregation-api.json`

**Environment Variables**:
```json
{
  "base_url": "http://localhost:8181",
  "arb_name": "security",
  "app_id": "APM100001"
}
```

---

## Current Data (As of 2025-10-12)

### Available Data

| ARB | Domain Risks | Total Items |
|-----|--------------|-------------|
| security | 10 | 126 |
| data | 17 | ~110 |
| operations | 15 | ~95 |
| enterprise_architecture | 9 | ~54 |

**Total**: 51 domain risks, 385 risk items

### Sample Apps with Data

- `APM100001` - Security (14 items)
- `APM100002` - Security (13 items)
- `APM100003` - Security, Integrity (multiple domains)
- `APM100005` - Resilience, Security
- `APM100006` - Security
- `APM100007` - Security

---

## Error Handling

### Common Errors

**404 Not Found** - ARB name not found or no data
```json
{
  "status": 404,
  "error": "Not Found",
  "message": "No domain risks found for ARB: unknown_arb"
}
```

**Frontend Handling**:
```typescript
try {
  const dashboard = await fetchDashboard(arbName);
  // render dashboard
} catch (error) {
  if (error.status === 404) {
    // Show empty state: "No risks found for this ARB"
  } else {
    // Show generic error message
  }
}
```

---

## Performance & Caching

### Recommended Strategy

1. **Initial Load**: Fetch `/dashboard` endpoint
2. **Refresh Interval**: Poll every 60 seconds
3. **Caching**: Cache for 60 seconds, invalidate on user actions
4. **Loading States**: Show skeleton screens during fetch
5. **Optimistic Updates**: Update UI immediately, rollback on error

### Response Times (Actual)

- Dashboard endpoint: ~100-200ms
- Domain risks list: ~50-100ms
- Risk items: ~50-150ms

---

## Next Steps

1. ✅ **Review this document** with your team
2. 📊 **Design mockups** based on recommended layout
3. 🔧 **Set up API client** (fetch/axios)
4. 🧪 **Test endpoints** using cURL or Insomnia
5. 🎨 **Build components** iteratively:
   - Start with dashboard overview
   - Add domain breakdown cards
   - Implement drill-down navigation
   - Add charts/visualizations
6. 🚀 **Deploy** and gather feedback

---

## Support & Resources

### Documentation

- **Complete API Spec**: `SME_DASHBOARD_SPEC.md`
- **Migration Guide**: `RISK_MIGRATION_GUIDE.md`
- **Architecture**: `CLAUDE.md` → Risk Aggregation section
- **Testing**: Import `insomnia-risk-aggregation-api.json`

### Questions?

- Backend issues: Check application logs
- API questions: Reference `SME_DASHBOARD_SPEC.md`
- Data questions: Reference `RISK_MIGRATION_GUIDE.md`

---

**Document Version**: 1.0
**Last Updated**: 2025-10-12
**Status**: Ready for Frontend Development
**Backend Service**: Running on port 8181 (lct_data_v2)
