# Risk Aggregation Testing Guide

## Quick Start

This guide helps you test the new risk aggregation architecture end-to-end.

## Prerequisites

- Spring Boot application running on `http://localhost:8080`
- PostgreSQL database with migrations applied
- Insomnia REST client installed

## Setup Steps

### 1. Apply Database Migrations

```bash
./mvnw flyway:migrate
```

This will create the `domain_risk` and `risk_item` tables.

### 2. Start the Application

```bash
./mvnw spring-boot:run
```

Or with your IDE (run `GatewayApplication.java`)

### 3. Import Insomnia Collection

1. Open Insomnia
2. Go to: Application → Preferences → Data → Import Data
3. Select: `insomnia-risk-aggregation-api.json`
4. Confirm import

## Testing Scenarios

### Scenario 1: Trigger Risk Auto-Creation

**Objective**: Verify that submitting evidence triggers risk item creation and domain risk aggregation.

**Steps**:

1. **Create an evidence submission** (use existing evidence submission endpoint)
   ```
   POST /api/v1/evidence
   {
     "appId": "test-app-001",
     "fieldKey": "encryption_at_rest",
     "profileFieldId": "pf-123",
     // ... other fields
   }
   ```

2. **Check if risk item was created**
   - In Insomnia, run: "Get Risk Items for App"
   - Set `app_id` environment variable to `test-app-001`
   - You should see a new risk item with:
     - `priority`: Based on registry rule (e.g., CRITICAL)
     - `priorityScore`: Calculated score (e.g., 100)
     - `evidenceStatus`: "missing"
     - `status`: OPEN

3. **Check domain risk aggregation**
   - Run: "Get Domain Risks for App"
   - You should see a domain risk with:
     - `domain`: "security" (for encryption_at_rest)
     - `totalItems`: 1
     - `openItems`: 1
     - `priorityScore`: Calculated
     - `assignedArb`: "security_arb"

**Expected Result**: ✅ Risk item created and linked to domain risk

---

### Scenario 2: ARB Workbench View

**Objective**: Verify ARB can see all their domain risks prioritized.

**Steps**:

1. **Query ARB risks**
   - In Insomnia, run: "Get Domain Risks for ARB"
   - Set `arb_name` to `security_arb`
   - Filter by status: `PENDING_ARB_REVIEW,UNDER_ARB_REVIEW`

2. **Verify response**
   - Should return array of domain risks
   - Sorted by `priorityScore DESC`
   - Each risk shows aggregate counts:
     - `totalItems`
     - `openItems`
     - `highPriorityItems`

3. **Check ARB dashboard**
   - Run: "Get ARB Dashboard Summary"
   - Should show statistics grouped by domain:
     - `domain`, `count`, `totalOpenItems`, `avgPriorityScore`

**Expected Result**: ✅ ARB sees aggregated view of all domain risks

---

### Scenario 3: Domain Risk Drill-Down

**Objective**: ARB drills down from domain level to evidence level.

**Steps**:

1. **Get domain risks**
   - Run: "Get Domain Risks for ARB"
   - Copy a `domainRiskId` from response

2. **Drill down to items**
   - Update environment variable: `domain_risk_id`
   - Run: "Get Risk Items for Domain"
   - Should return all risk items for that domain

3. **Inspect individual item**
   - Copy a `riskItemId`
   - Update environment variable: `risk_item_id`
   - Run: "Get Risk Item by ID"
   - Should show full details including:
     - `triggeringEvidenceId`
     - `policyRequirementSnapshot`
     - Priority and severity

**Expected Result**: ✅ Can navigate from domain → items → item details

---

### Scenario 4: PO Workbench View

**Objective**: Product Owner sees all their app's risk items prioritized.

**Steps**:

1. **Get all risk items for app**
   - Run: "Get Risk Items for App"
   - Set `app_id` to your test app
   - Should return items sorted by priority score (highest first)

2. **Filter by status**
   - Run: "Get Risk Items by Status"
   - Try different statuses: OPEN, IN_PROGRESS, RESOLVED

3. **Field-specific analysis**
   - Run: "Get Risk Items by Field"
   - Set `field_key` to `encryption_at_rest`
   - See all risks for this specific field across all evidence

**Expected Result**: ✅ PO sees evidence-level view with filtering options

---

### Scenario 5: Risk Resolution Workflow

**Objective**: Resolve risk items and verify domain risk updates.

**Steps**:

1. **Get initial state**
   - Run: "Get Domain Risk by ID"
   - Note: `openItems`, `priorityScore`

2. **Resolve a risk item**
   - Update `risk_item_id` in environment
   - Run: "Example: Resolve Risk Item"
   - Payload:
     ```json
     {
       "status": "RESOLVED",
       "resolution": "REMEDIATED",
       "resolutionComment": "Fixed encryption configuration"
     }
     ```

3. **Verify item updated**
   - Run: "Get Risk Item by ID"
   - Should show:
     - `status`: RESOLVED
     - `resolution`: REMEDIATED
     - `resolvedAt`: timestamp
     - `resolutionComment`: your comment

4. **Check domain risk recalculated**
   - Run: "Get Domain Risk by ID" again
   - Should show:
     - `openItems`: decreased by 1
     - `priorityScore`: recalculated
     - `updatedAt`: new timestamp

5. **Test auto-transition**
   - If you resolved the last open item:
   - Domain risk `status` should auto-transition to RESOLVED

**Expected Result**: ✅ Resolving items triggers automatic aggregation recalculation

---

### Scenario 6: Multiple Status Transitions

**Objective**: Test various status transitions and their effects.

**Steps**:

1. **In Progress**
   ```json
   {
     "status": "IN_PROGRESS",
     "resolution": null,
     "resolutionComment": "Working on fix"
   }
   ```

2. **Waive**
   ```json
   {
     "status": "WAIVED",
     "resolution": "RISK_ACCEPTED",
     "resolutionComment": "Risk accepted with compensating controls"
   }
   ```

3. **Close**
   ```json
   {
     "status": "CLOSED",
     "resolution": "VERIFIED",
     "resolutionComment": "Verified by ARB, closing"
   }
   ```

**Expected Result**: ✅ All status transitions work correctly

---

### Scenario 7: Evidence Traceability

**Objective**: Trace from evidence → risk items → domain risk.

**Steps**:

1. **Find evidence**
   - Note an `evidenceId` from your evidence submissions

2. **Find related risk items**
   - Run: "Get Risk Items by Evidence"
   - Set `evidence_id` in environment
   - Should show all risk items triggered by this evidence

3. **Check domain risks**
   - For each risk item, note the `domainRiskId`
   - Run: "Get Domain Risk by ID"
   - Should show the domain risk containing these items

**Expected Result**: ✅ Complete traceability chain maintained

---

## Validation Checklist

Use this checklist to verify the implementation:

### ✅ Database Layer
- [ ] Migrations applied successfully
- [ ] Foreign key constraints working
- [ ] Indexes created for performance
- [ ] Timestamp triggers auto-updating

### ✅ Service Layer
- [ ] RiskAutoCreationService creates RiskItem + DomainRisk
- [ ] RiskPriorityCalculator computes scores correctly
- [ ] ArbRoutingService routes to correct ARB
- [ ] DomainRiskAggregationService recalculates on updates
- [ ] Status auto-transitions work (RESOLVED when all closed)

### ✅ API Layer
- [ ] All GET endpoints return 200 OK with data
- [ ] PATCH endpoint updates status successfully
- [ ] Status filters work correctly
- [ ] 404 returned for non-existent resources
- [ ] Response DTOs match entity data
- [ ] Logging shows request/response details

### ✅ Business Logic
- [ ] Priority scoring formula works correctly
- [ ] Evidence status multipliers applied
- [ ] Domain score bonuses calculated
- [ ] High priority item counts accurate
- [ ] Domain risk auto-resolves when items closed
- [ ] Domain risk reopens when new items added

### ✅ Integration
- [ ] Evidence submission triggers risk creation
- [ ] Risk items link to correct domain risk
- [ ] ARBs see only their assigned domain risks
- [ ] POs see all items for their apps
- [ ] Status updates visible immediately
- [ ] No N+1 query issues

---

## Common Issues & Fixes

### Issue: No risk items created after evidence submission

**Cause**: Field doesn't have `requires_review: true` in registry for that criticality.

**Fix**: Check `profile-fields.registry.yaml` and ensure the field has a rule with `requires_review: true` for your app's criticality level.

---

### Issue: Domain risk not found

**Cause**: Domain risk might not exist yet, or wrong app/domain combination.

**Fix**: First trigger risk item creation which will auto-create the domain risk.

---

### Issue: Priority score seems wrong

**Cause**: Evidence status not set, or using default LOW priority.

**Fix**:
- Update registry to include priority in rules
- Ensure evidence status is passed correctly
- Check RiskPriorityCalculator multipliers

---

### Issue: ARB not seeing domain risks

**Cause**: Wrong ARB name or status filter excluding results.

**Fix**:
- Check `arb_routing` in registry YAML
- Try removing status filter: `?status=` (all statuses)
- Verify domain risk has `assignedArb` set correctly

---

## Performance Testing

### Load Test Scenarios

1. **Create 100 risk items for one app**
   - Measure: Domain risk recalculation time
   - Expected: < 500ms per update

2. **Query 1000 domain risks**
   - Measure: API response time
   - Expected: < 2s with indexes

3. **Update 50 risk item statuses**
   - Measure: Aggregation overhead
   - Expected: < 100ms per update

### Monitoring Points

- Database query times (check slow query log)
- API endpoint response times (check logs)
- Memory usage during aggregation
- Transaction rollback rates

---

## Test Data Generator (Optional)

Create a simple script to generate test data:

```bash
# Generate 10 risk items for testing
for i in {1..10}; do
  curl -X POST http://localhost:8080/api/v1/evidence \
    -H "Content-Type: application/json" \
    -d "{
      \"appId\": \"test-app-001\",
      \"fieldKey\": \"encryption_at_rest\",
      \"profileFieldId\": \"pf-$i\"
    }"
done
```

---

## Next Steps

After completing these tests:

1. ✅ Verify all scenarios pass
2. 📝 Document any issues found
3. 🔄 Run automated test suite: `./mvnw test`
4. 🚀 Ready for code review and merge!
