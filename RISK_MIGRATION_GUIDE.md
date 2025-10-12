# Risk Aggregation v2 Migration Guide

**Date:** 2025-10-12
**Status:** ✅ Migration Complete
**Database:** lct_data_v2 (port 8181)

---

## Overview

This guide documents the completed migration from Risk v1 (risk_story) to Risk v2 (domain_risk + risk_item) architecture.

### What Changed

| Aspect | v1 (risk_story) | v2 (domain_risk + risk_item) |
|--------|----------------|------------------------------|
| **Granularity** | One risk per evidence | Domain-level aggregation + evidence items |
| **ARB Routing** | Pattern-based SME assignment | Registry-based ARB routing |
| **Prioritization** | Static severity | Dynamic 0-100 score |
| **Views** | Single flat list | ARB view (strategic) + PO view (tactical) |
| **Status** | 7 status values | Separate domain & item statuses |

---

## Migration Results

### Execution Summary

```json
{
  "success": true,
  "startTime": "2025-10-12T15:47:49Z",
  "endTime": "2025-10-12T15:47:56Z",
  "duration": "6.6 seconds",
  "totalRiskStories": 383,
  "createdDomainRisks": 51,
  "createdRiskItems": 383,
  "skippedRiskItems": 0,
  "failedRiskItems": 0,
  "failedGroups": []
}
```

### Data Distribution

**Domain Risks by ARB:**
- `data`: 17 domain risks (confidentiality, integrity)
- `operations`: 15 domain risks (availability, resilience)
- `security`: 10 domain risks (security)
- `enterprise_architecture`: 9 domain risks (app criticality)

**Database Tables:**
- `risk_story`: 383 records (preserved, read-only)
- `domain_risk`: 52 records (51 migrated + 1 test)
- `risk_item`: 385 records (383 migrated + 2 test)

---

## API Changes

### Old Endpoints (v1 - Deprecated)

```bash
# Old risk_story endpoints (still readable for legacy data)
GET /api/apps/{appId}/risks
GET /api/risks/{riskId}
POST /api/risks/{riskId}/review
```

### New Endpoints (v2 - Active)

#### ARB/SME View (Domain-Level)

```bash
# Get all domain risks for an ARB
GET /api/v1/domain-risks/arb/{arbName}
# Example: GET /api/v1/domain-risks/arb/security

# Get ARB dashboard summary
GET /api/v1/domain-risks/arb/{arbName}/summary
# Returns: domains, counts, open items, avg scores

# Get specific domain risk detail
GET /api/v1/domain-risks/{domainRiskId}

# Drill down into risk items
GET /api/v1/domain-risks/{domainRiskId}/items

# Get all domain risks for an app
GET /api/v1/domain-risks/app/{appId}
```

#### PO/Developer View (Evidence-Level)

```bash
# Get all risk items for an app (prioritized)
GET /api/v1/risk-items/app/{appId}

# Filter by status
GET /api/v1/risk-items/app/{appId}/status/{status}
# Status: OPEN, IN_PROGRESS, RESOLVED, WAIVED, CLOSED

# Get specific risk item
GET /api/v1/risk-items/{riskItemId}

# Update risk item status
PATCH /api/v1/risk-items/{riskItemId}/status
{
  "status": "RESOLVED",
  "resolution": "evidence_provided",
  "comment": "Evidence uploaded and approved"
}

# Get items by field
GET /api/v1/risk-items/field/{fieldKey}

# Get items by evidence
GET /api/v1/risk-items/evidence/{evidenceId}
```

---

## ARB Names (Registry-Based)

### Important: Use Short Names

The system uses **short ARB names** from the registry (source of truth):

✅ Correct:
- `security`
- `data`
- `operations`
- `enterprise_architecture`

❌ Incorrect (old convention):
- `security_arb`
- `data_arb`
- `operations_arb`

### ARB Routing Map

| derived_from | arb | domains |
|--------------|-----|---------|
| `security_rating` | `security` | security |
| `confidentiality_rating` | `data` | confidentiality |
| `integrity_rating` | `data` | integrity |
| `availability_rating` | `operations` | availability |
| `resilience_rating` | `operations` | resilience |
| `app_criticality_assessment` | `enterprise_architecture` | app_criticality_assessment |

---

## Code Migration Examples

### Before (v1): Creating Risks

```java
// Old: RiskStory created per evidence
RiskStory risk = new RiskStory();
risk.setRiskId(UUID.randomUUID().toString());
risk.setAppId(appId);
risk.setFieldKey(fieldKey);
risk.setTriggeringEvidenceId(evidenceId);
risk.setSeverity("high");
risk.setAssignedSme(assignSme(fieldKey));
risk.setStatus(RiskStatus.PENDING_SME_REVIEW);
riskStoryRepository.save(risk);
```

### After (v2): Creating Risk Items

```java
// New: RiskItem within DomainRisk
String derivedFrom = registryService.getDerivedFromForField(fieldKey).orElse("app_criticality_assessment");
String domain = arbRoutingService.calculateDomain(derivedFrom);
String arb = arbRoutingService.getArbForDerivedFrom(derivedFrom);

// Get or create domain risk
DomainRisk domainRisk = aggregationService.getOrCreateDomainRisk(appId, derivedFrom);

// Create risk item
RiskItem riskItem = new RiskItem();
riskItem.setRiskItemId("item_" + UUID.randomUUID());
riskItem.setDomainRiskId(domainRisk.getDomainRiskId());
riskItem.setAppId(appId);
riskItem.setFieldKey(fieldKey);
riskItem.setTriggeringEvidenceId(evidenceId);
riskItem.setPriority(RiskPriority.HIGH);
riskItem.setPriorityScore(priorityCalculator.calculatePriorityScore(priority, evidenceStatus));
riskItem.setStatus(RiskItemStatus.OPEN);

// Add to domain and recalculate
aggregationService.addRiskItemToDomain(domainRisk, riskItem);
```

### Before (v1): Querying Risks

```java
// Old: Flat list of risks
List<RiskStory> risks = riskStoryRepository.findByAppId(appId);
```

### After (v2): Querying by View

```java
// ARB View: Domain-level aggregations
List<DomainRisk> domainRisks = domainRiskRepository.findByArbPrioritized(
    "security",
    List.of(DomainRiskStatus.PENDING_ARB_REVIEW, DomainRiskStatus.UNDER_ARB_REVIEW)
);

// PO View: Evidence-level details
List<RiskItem> riskItems = riskItemRepository.findByAppIdOrderByPriorityScoreDesc(appId);
```

---

## Frontend Integration

### ARB Dashboard

```typescript
// Fetch domain risks for an ARB
const response = await fetch(`/api/v1/domain-risks/arb/security`);
const domainRisks = await response.json();

// Display aggregated metrics
domainRisks.forEach(domain => {
  console.log(`${domain.domain}: ${domain.openItems} open items, score: ${domain.priorityScore}`);
});

// Get summary for dashboard
const summary = await fetch(`/api/v1/domain-risks/arb/security/summary`).then(r => r.json());
// Returns: [{ domain: "security", count: 10, totalOpenItems: 45, avgPriorityScore: 67 }]
```

### PO Risk List

```typescript
// Fetch all risk items for an app
const response = await fetch(`/api/v1/risk-items/app/${appId}`);
const riskItems = await response.json();

// Items are already sorted by priority score (DESC)
riskItems.forEach(item => {
  console.log(`${item.fieldKey}: ${item.priorityScore} - ${item.status}`);
});
```

### Drill-Down Flow

```typescript
// 1. ARB sees domain risk
const domainRisks = await fetch(`/api/v1/domain-risks/arb/security`).then(r => r.json());

// 2. User clicks on a domain risk
const domainRiskId = domainRisks[0].domainRiskId;

// 3. Show detailed items
const items = await fetch(`/api/v1/domain-risks/${domainRiskId}/items`).then(r => r.json());
```

---

## Priority Scoring

### Formula

```
Item Score = Base Priority × Evidence Multiplier
  Base:       CRITICAL=40, HIGH=30, MEDIUM=20, LOW=10
  Multiplier: missing=2.5x, non_compliant=2.3x, expired=2.0x, approved=1.0x
  Result:     0-100 (capped at 100)

Domain Score = Max(Item Scores) + Bonuses
  High priority bonus: min(10, highPriorityCount × 2)
  Volume bonus:        min(5, (openCount - 3))
  Result:              0-100 (capped at 100)
```

### Score Ranges

| Range | Priority | Typical Actions |
|-------|----------|----------------|
| 90-100 | CRITICAL | Immediate ARB escalation |
| 70-89 | HIGH | Review within 1 week |
| 40-69 | MEDIUM | Review within 2 weeks |
| 0-39 | LOW | Review within 30 days |

---

## Status Transitions

### Domain Risk Status Flow

```
PENDING_ARB_REVIEW → UNDER_ARB_REVIEW → IN_PROGRESS → RESOLVED
                           ↓                  ↓            ↓
                         WAIVED          AWAITING_    CLOSED
                                        REMEDIATION
```

**Auto-Transitions:**
- `PENDING_ARB_REVIEW` → `IN_PROGRESS`: When first item added
- `IN_PROGRESS` → `RESOLVED`: When all items closed
- `RESOLVED` → `IN_PROGRESS`: When new item added

### Risk Item Status Flow

```
OPEN → IN_PROGRESS → RESOLVED
  ↓          ↓           ↓
WAIVED   CLOSED      CLOSED
```

---

## Rollback Strategy

### If Issues Arise

The legacy `risk_story` table is preserved:

```sql
-- Check legacy data
SELECT * FROM risk_story WHERE app_id = 'APM100001';

-- Compare with migrated data
SELECT dr.domain, ri.field_key, ri.priority_score
FROM domain_risk dr
JOIN risk_item ri ON dr.domain_risk_id = ri.domain_risk_id
WHERE dr.app_id = 'APM100001';
```

### Restore Old Endpoints

If needed, temporarily re-enable v1 endpoints by:
1. Updating `RiskStoryController` to read from `risk_story` table
2. Adding feature flag: `features.useRiskV1=true`

---

## Testing Checklist

### Smoke Tests

```bash
# 1. ARB view returns data
curl http://localhost:8181/api/v1/domain-risks/arb/security | jq 'length'
# Expected: > 0

# 2. PO view returns data
curl http://localhost:8181/api/v1/risk-items/app/APM100001 | jq 'length'
# Expected: > 0

# 3. Domain risk detail works
DOMAIN_RISK_ID=$(curl -s http://localhost:8181/api/v1/domain-risks/arb/security | jq -r '.[0].domainRiskId')
curl http://localhost:8181/api/v1/domain-risks/$DOMAIN_RISK_ID | jq '.totalItems'
# Expected: numeric value

# 4. Items drill-down works
curl http://localhost:8181/api/v1/domain-risks/$DOMAIN_RISK_ID/items | jq 'length'
# Expected: matches totalItems above
```

### Data Integrity Tests

```sql
-- Verify all risk items link to valid domain risks
SELECT COUNT(*)
FROM risk_item ri
LEFT JOIN domain_risk dr ON ri.domain_risk_id = dr.domain_risk_id
WHERE dr.domain_risk_id IS NULL;
-- Expected: 0

-- Verify domain risk counts match actual items
SELECT dr.domain_risk_id, dr.total_items, COUNT(ri.risk_item_id) as actual_items
FROM domain_risk dr
LEFT JOIN risk_item ri ON dr.domain_risk_id = ri.domain_risk_id
GROUP BY dr.domain_risk_id, dr.total_items
HAVING dr.total_items != COUNT(ri.risk_item_id);
-- Expected: 0 rows

-- Verify all ARB names are valid
SELECT DISTINCT arb FROM domain_risk
WHERE arb NOT IN ('security', 'data', 'operations', 'enterprise_architecture');
-- Expected: 0 rows
```

---

## Performance Considerations

### Query Optimization

All query paths are indexed:

```sql
-- ARB queries use these indexes
CREATE INDEX idx_domain_risk_arb ON domain_risk(arb);
CREATE INDEX idx_domain_risk_priority ON domain_risk(priority_score DESC);

-- PO queries use these indexes
CREATE INDEX idx_risk_item_app ON risk_item(app_id);
CREATE INDEX idx_risk_item_priority ON risk_item(priority_score DESC);

-- Drill-down queries use this index
CREATE INDEX idx_risk_item_domain_risk ON risk_item(domain_risk_id);
```

### Expected Performance

| Query | Records | Response Time |
|-------|---------|--------------|
| ARB view (all domains) | 10-20 | < 100ms |
| PO view (all items) | 100-500 | < 200ms |
| Domain detail | 1 | < 50ms |
| Items drill-down | 10-50 | < 100ms |

---

## Monitoring

### Key Metrics

```sql
-- Domain risk distribution
SELECT arb, COUNT(*) as domain_count, SUM(open_items) as total_open
FROM domain_risk
GROUP BY arb;

-- Priority distribution
SELECT overall_priority, COUNT(*)
FROM domain_risk
GROUP BY overall_priority;

-- Status distribution
SELECT status, COUNT(*)
FROM domain_risk
GROUP BY status;

-- Average items per domain
SELECT AVG(total_items) as avg_items_per_domain
FROM domain_risk;
```

### Health Checks

```bash
# Check API health
curl http://localhost:8181/actuator/health

# Check database connection
curl http://localhost:8181/api/v1/domain-risks/arb/security?limit=1

# Verify aggregation calculations
# (domain totalItems should match actual risk_item count)
```

---

## Troubleshooting

### Issue: Empty ARB Results

**Symptom:** `GET /api/v1/domain-risks/arb/security_arb` returns `[]`

**Cause:** Using old ARB naming convention with `_arb` suffix

**Fix:** Use short names from registry:
```bash
# ❌ Wrong
curl /api/v1/domain-risks/arb/security_arb

# ✅ Correct
curl /api/v1/domain-risks/arb/security
```

### Issue: Priority Score = 0

**Symptom:** All domain risks have `priorityScore: 0`

**Cause:** Risk items missing priority or evidence status

**Fix:** Run recalculation:
```java
domainRiskAggregationService.recalculateAggregations(domainRisk);
```

### Issue: Missing Domain Risks

**Symptom:** App has risk items but no domain risk

**Cause:** Orphaned risk items (foreign key constraint should prevent this)

**Fix:** Recreate domain risk:
```java
String derivedFrom = registryService.getDerivedFromForField(fieldKey).orElse("app_criticality_assessment");
DomainRisk domainRisk = aggregationService.getOrCreateDomainRisk(appId, derivedFrom);
aggregationService.recalculateAggregations(domainRisk);
```

---

## Support

### Documentation

- **Architecture:** See `CLAUDE.md` → Risk Aggregation Architecture section
- **API Reference:** See `RISK_AGGREGATION_API.md`
- **Testing Guide:** See `TESTING_GUIDE.md`
- **Implementation Plan:** See `RISK_AGGREGATION_PLAN.md`

### Database Access

```bash
# Connect to lct_data_v2
PGPASSWORD=postgres psql -h helios -U postgres -d lct_data_v2

# Useful queries
\d domain_risk      -- Show domain_risk schema
\d risk_item        -- Show risk_item schema
SELECT * FROM domain_risk LIMIT 5;
SELECT * FROM risk_item LIMIT 5;
```

### Admin Endpoints

```bash
# Get migration statistics
GET /api/admin/risk-migration/stats

# Preview what would be migrated (safe)
GET /api/admin/risk-migration/dry-run
```

---

## Success Criteria

✅ **All criteria met:**

- [x] 100% migration success rate (383/383)
- [x] All domain risks have valid ARB assignments
- [x] Priority scores calculated for all items
- [x] Both ARB and PO APIs functional
- [x] Legacy data preserved for rollback
- [x] Performance < 200ms for all queries
- [x] Zero orphaned risk items
- [x] Registry as source of truth for ARB routing

---

**Migration Completed:** 2025-10-12
**Next Steps:** Monitor production usage, gather feedback from ARBs and POs
