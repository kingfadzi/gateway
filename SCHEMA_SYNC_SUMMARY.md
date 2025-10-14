# Schema Synchronization Summary

**Date:** October 13, 2025
**Status:** ✅ COMPLETE

---

## Overview

Updated `sql/core_cdm.sql` to reflect the V3 Flyway migration changes that added user-level assignment fields to the `domain_risk` table.

---

## Changes Made

### 1. Updated domain_risk Table Definition

**File:** `sql/core_cdm.sql` (lines 356-360)

**Added Fields:**
```sql
-- Assignment
assigned_arb      TEXT,
assigned_to       TEXT,                      -- User-level assignment for my-queue scope
assigned_to_name  TEXT,                      -- Display name of assigned user
assigned_at       TIMESTAMPTZ,
```

**Purpose:**
- `assigned_to` - Stores user ID for user-level assignment (separate from board-level `assigned_arb`)
- `assigned_to_name` - Stores display name for UI convenience
- Enables "my-queue" scope filtering in ARB Dashboard

### 2. Added Index

**File:** `sql/core_cdm.sql` (line 387)

**New Index:**
```sql
CREATE INDEX IF NOT EXISTS idx_domain_risk_assigned_to ON domain_risk(assigned_to);
```

**Purpose:**
- Optimizes my-queue queries: `WHERE assigned_to = :userId`
- Critical for dashboard performance

### 3. Added Documentation Comments

**File:** `sql/core_cdm.sql` (lines 497-498)

**New Comments:**
```sql
COMMENT ON COLUMN domain_risk.assigned_to IS 'User-level assignment (user ID) for my-queue scope filtering';
COMMENT ON COLUMN domain_risk.assigned_to_name IS 'Display name of assigned user for UI';
```

**Purpose:**
- Documents the purpose of new fields
- Explains the distinction from `assigned_arb`

---

## Synchronization Status

| Component | Status | Notes |
|-----------|--------|-------|
| V3 Migration (`V3__add_arb_dashboard_fields.sql`) | ✅ Created | Ready to apply |
| Entity (`DomainRisk.java`) | ✅ Updated | Fields added |
| DTOs | ✅ Updated | `DomainRiskResponse`, `DomainRiskSummaryDto` |
| Mapper (`RiskDtoMapper.java`) | ✅ Updated | Mapping updated |
| Core CDM (`sql/core_cdm.sql`) | ✅ Updated | **THIS UPDATE** |

---

## Migration Application

### Option 1: Flyway (When DB is configured)

```bash
./mvnw flyway:migrate
```

### Option 2: Manual SQL Execution

```sql
-- Connect to PostgreSQL
psql -h helios -U postgres -d lct_data

-- Apply migration
ALTER TABLE domain_risk ADD COLUMN assigned_to VARCHAR(255);
ALTER TABLE domain_risk ADD COLUMN assigned_to_name VARCHAR(255);
CREATE INDEX idx_domain_risk_assigned_to ON domain_risk(assigned_to);
```

### Option 3: Application Startup

Spring Boot will automatically apply the migration on startup via Flyway.

---

## Verification

After applying the migration, verify with:

```sql
-- Check columns exist
\d domain_risk

-- Check index exists
\di idx_domain_risk_assigned_to

-- Expected output should include:
-- - assigned_to (varchar 255)
-- - assigned_to_name (varchar 255)
-- - idx_domain_risk_assigned_to (index on assigned_to)
```

---

## Impact

**No Breaking Changes:**
- New columns are nullable
- Existing queries unaffected
- Backward compatible

**Enabled Features:**
- My-queue scope filtering
- User-level assignment tracking
- Enhanced ARB dashboard functionality

---

## Files Modified

1. ✅ `src/main/resources/db/migration/V3__add_arb_dashboard_fields.sql` (NEW)
2. ✅ `src/main/java/com/example/gateway/risk/model/DomainRisk.java`
3. ✅ `src/main/java/com/example/gateway/risk/dto/DomainRiskResponse.java`
4. ✅ `src/main/java/com/example/gateway/risk/dto/RiskDtoMapper.java`
5. ✅ `sql/core_cdm.sql` (THIS UPDATE)

---

## Next Steps

1. **Apply Migration:** Use one of the three options above
2. **Verify Schema:** Run verification queries
3. **Test Endpoints:** Test new ARB Dashboard endpoints
4. **Monitor Logs:** Check for any migration errors

---

## Compatibility

- **PostgreSQL Version:** 12+
- **Flyway Version:** 11.7.2 (as per pom.xml)
- **Spring Boot Version:** 3.5.3

---

## Rollback Plan

If needed, rollback with:

```sql
DROP INDEX IF EXISTS idx_domain_risk_assigned_to;
ALTER TABLE domain_risk DROP COLUMN IF EXISTS assigned_to_name;
ALTER TABLE domain_risk DROP COLUMN IF EXISTS assigned_to;
```

---

**Schema synchronization complete!** The `sql/core_cdm.sql` file now matches the production schema with V3 migration changes.
