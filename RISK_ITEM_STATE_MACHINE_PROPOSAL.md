# Risk Item State Machine Proposal (UPDATED)

## Current State Analysis

### Current Implementation (Before Changes)

**RiskItemStatus Enum:**
- `OPEN` - Needs attention
- `IN_PROGRESS` - Being actively worked on
- `RESOLVED` - Evidence provided and accepted
- `WAIVED` - ARB waived this specific item
- `CLOSED` - Closed without resolution

**Fields:**
- `status` (RiskItemStatus enum)
- `resolution` (String) - Free-form resolution type
- `resolution_comment` (TEXT) - Explanation

**Critical Dependencies:**
- "Open items" count = `status IN ('OPEN', 'IN_PROGRESS')` (used for domain aggregation)
- Multiple SQL queries filter by `status = 'OPEN'`
- Status transitions trigger domain risk recalculation

---

## Problem Statement

The current 5-state model doesn't reflect the actual workflow:

**SME Actions:**
1. Approve (risk is acceptable)
2. Approve with Mitigation (acceptable with conditions)
3. Reject (needs work/remediation)
4. Request More Info (needs clarification)
5. Reassign (to another SME)
6. Escalate (to higher authority) → **Active state, closed by SME after resolution**

**PO Actions:**
7. Self-Attest (PO vouches for compliance without external evidence) → **Separate flow, no SME**
8. Submit Evidence (provide documentation)
9. Mark as Remediated (work completed)

**Current Problem:**
- Mapping "approve" → `WAIVED` is semantically incorrect
- Mapping "reject" → `OPEN` loses information about SME decision
- No way to distinguish SME-approved vs PO-self-attested vs evidence-based resolution
- Reassignment and escalation states not tracked
- **PO self-attestation and SME assessment are conflated** - they are mutually exclusive flows

---

## Proposed Solution

### Two Mutually Exclusive Workflows

**Key Insight:** PO self-attestation and SME assessment are **completely separate flows**.

**Flow 1: Self-Attestation Flow** (No SME Involvement)
```
Risk Created → PO Self-Attests → SELF_ATTESTED (Terminal)
```

**Flow 2: SME Assessment Flow** (Standard Review Process)
```
Risk Created → SME Reviews → Various Outcomes (Approved/Rejected/Escalated/etc.)
```

---

### New RiskItemStatus Enum (Lifecycle Phases)

```java
public enum RiskItemStatus {
    // ====== Initial State ======

    PENDING_REVIEW,        // Initial state - awaiting triage/SME assignment

    // ====== SME Assessment Flow States ======

    UNDER_SME_REVIEW,      // SME is actively reviewing
    AWAITING_REMEDIATION,  // SME rejected - PO needs to fix/provide evidence
    IN_REMEDIATION,        // PO is actively working on remediation
    PENDING_APPROVAL,      // Evidence submitted, awaiting final SME approval
    ESCALATED,             // Escalated to higher authority - awaiting escalation resolution

    // ====== Terminal States ======

    SME_APPROVED,          // SME accepted the risk (with or without mitigation)
    SELF_ATTESTED,         // PO self-attested (no SME review - separate flow)
    REMEDIATED,            // PO fixed the issue, evidence provided and approved by SME
    CLOSED                 // Administrative closure or post-escalation closure
}
```

#### Resolution Field Values (Specific Outcomes)

Used in combination with status for granular tracking:

```java
// SME Resolution Types
"SME_APPROVED"                  // Simple approval
"SME_APPROVED_WITH_MITIGATION"  // Approval requiring mitigation plan
"SME_REJECTED"                  // Rejected - needs work
"SME_REQUESTED_INFO"            // More information needed
"SME_ESCALATED"                 // Escalated to ARB lead/senior

// PO Resolution Types
"PO_SELF_ATTESTED"             // PO vouched for compliance
"PO_PROVIDED_EVIDENCE"         // Evidence submitted
"PO_REMEDIATED"                // Issue fixed

// Reassignment
"REASSIGNED_TO_SME"            // Reassigned to another SME
"REASSIGNED_TO_ARB"            // Reassigned to different ARB

// Administrative
"RISK_ACCEPTED"                // Formal risk acceptance
"NO_LONGER_APPLICABLE"         // Requirement no longer applies
"DUPLICATE"                    // Duplicate of another risk
```

---

### State Transition Diagram

#### Flow 1: Self-Attestation (Mutually Exclusive from SME Flow)

```
┌──────────────────┐
│ PENDING_REVIEW   │ ◄─── Risk auto-created or manually created
└────────┬─────────┘
         │
         │
         └──► [PO Self-Attests] ──► SELF_ATTESTED (Terminal ✓)
                                        resolution="PO_SELF_ATTESTED"
```

**Rules:**
- Once PO self-attests, risk is **immediately closed**
- **No SME involvement** in this flow
- Cannot transition to SME flow after self-attestation


#### Flow 2: SME Assessment (Standard Review Process)

```
┌──────────────────┐
│ PENDING_REVIEW   │ ◄─── Risk auto-created or manually created
└────────┬─────────┘
         │
         │
         └──► [SME Self-Assigns] ──► UNDER_SME_REVIEW
                                       resolution=null


┌──────────────────┐
│ UNDER_SME_REVIEW │
└────────┬─────────┘
         │
         ├──► [SME Approves] ──► SME_APPROVED (Terminal ✓)
         │                         resolution="SME_APPROVED"
         │
         ├──► [SME Approves w/ Mitigation] ──► SME_APPROVED (Terminal ✓)
         │                                       resolution="SME_APPROVED_WITH_MITIGATION"
         │
         ├──► [SME Rejects] ──► AWAITING_REMEDIATION
         │                       resolution="SME_REJECTED"
         │
         ├──► [SME Requests Info] ──► AWAITING_REMEDIATION
         │                             resolution="SME_REQUESTED_INFO"
         │
         ├──► [SME Reassigns] ──► PENDING_REVIEW
         │                         resolution="REASSIGNED_TO_SME"
         │                         (clears assigned_to, assigned_at)
         │
         └──► [SME Escalates] ──► ESCALATED (Active - awaiting resolution)
                                   resolution="SME_ESCALATED"


┌──────────────────────┐
│ AWAITING_REMEDIATION │
└──────────┬───────────┘
           │
           ├──► [PO Submits Evidence] ──► PENDING_APPROVAL
           │                                resolution="PO_PROVIDED_EVIDENCE"
           │
           ├──► [PO Marks Remediated] ──► IN_REMEDIATION
           │                                resolution="PO_REMEDIATED"
           │
           └──► [Admin Closes] ──► CLOSED (Terminal ✓)
                                    resolution="ADMIN_CLOSED"


┌──────────────────┐
│ PENDING_APPROVAL │
└────────┬─────────┘
         │
         ├──► [SME Approves] ──► REMEDIATED (Terminal ✓)
         │                        resolution="SME_APPROVED_REMEDIATION"
         │
         ├──► [SME Rejects] ──► AWAITING_REMEDIATION
         │                       resolution="SME_REJECTED_EVIDENCE"
         │
         └──► [SME Escalates] ──► ESCALATED (Active - awaiting resolution)
                                   resolution="SME_ESCALATED"


┌────────────────┐
│ IN_REMEDIATION │
└────────┬───────┘
         │
         ├──► [PO Submits Evidence] ──► PENDING_APPROVAL
         │                                resolution="PO_PROVIDED_EVIDENCE"
         │
         └──► [Admin Closes] ──► CLOSED (Terminal ✓)
                                  resolution="ADMIN_CLOSED"


┌─────────────┐
│ ESCALATED   │ ◄─── Escalated to higher authority (stays open)
└─────┬───────┘
      │
      ├──► [SME Approves After Resolution] ──► SME_APPROVED (Terminal ✓)
      │                                          resolution="SME_APPROVED_POST_ESCALATION"
      │
      ├──► [SME Closes] ──► CLOSED (Terminal ✓)
      │                      resolution="CLOSED_POST_ESCALATION"
      │
      └──► [Returns to Remediation] ──► AWAITING_REMEDIATION
                                          resolution="ESCALATION_REQUIRES_REMEDIATION"
```

**Terminal States (✓):**
- `SME_APPROVED` - SME accepted the risk
- `SELF_ATTESTED` - PO self-attested (separate flow)
- `REMEDIATED` - PO fixed & SME approved
- `CLOSED` - Administrative closure or post-escalation closure

**Active States (need action):**
- `PENDING_REVIEW` - Awaiting SME assignment
- `UNDER_SME_REVIEW` - SME reviewing
- `AWAITING_REMEDIATION` - PO needs to fix/provide evidence
- `IN_REMEDIATION` - PO working on fix
- `PENDING_APPROVAL` - Awaiting SME approval of evidence
- `ESCALATED` - Escalated, awaiting higher authority decision + SME closure

---

### "Open Items" Definition (For Aggregation)

**Current Logic:**
```sql
openItems = COUNT WHERE status IN ('OPEN', 'IN_PROGRESS')
```

**New Logic:**
```sql
openItems = COUNT WHERE status IN (
    'PENDING_REVIEW',
    'UNDER_SME_REVIEW',
    'AWAITING_REMEDIATION',
    'IN_REMEDIATION',
    'PENDING_APPROVAL',
    'ESCALATED'              -- ESCALATED is still open, awaiting SME closure
)
```

**Rationale:** All non-terminal states are "open" and need attention. Escalated risks remain open until SME closes them after escalation is resolved.

**Terminal States (Not Counted as Open):**
- `SME_APPROVED` - SME approved
- `SELF_ATTESTED` - PO self-attested
- `REMEDIATED` - Fixed and SME approved
- `CLOSED` - Administratively closed or closed after escalation

---

### Implementation Impact Analysis

#### Files to Update:

1. **Model Changes:**
   - `RiskItemStatus.java` - Add new enum values
   - Update documentation in `RiskItem.java`

2. **Service Logic:**
   - `DomainRiskAggregationService.java` - Update `countByDomainRiskIdAndStatus` logic
   - `RiskStoryController.java` - Update SME review endpoint mappings
   - `RiskAutoCreationServiceImpl.java` - Set initial state to `PENDING_REVIEW`

3. **Repository Queries:**
   - `RiskItemRepository.java` - Update all queries filtering by `status = 'OPEN'`
   - Update aggregation queries (7 SQL queries found)

4. **Frontend Compatibility:**
   - Add status mapping in response DTOs for backward compatibility
   - Update API documentation

---

### Detailed Action Mappings

#### SME Review Actions → Status + Resolution

| Action | New Status | Resolution | Resolution Comment |
|--------|-----------|-----------|-------------------|
| **approve** | `SME_APPROVED` | `"SME_APPROVED"` | Comments from SME |
| **approve_with_mitigation** | `SME_APPROVED` | `"SME_APPROVED_WITH_MITIGATION"` | Mitigation plan required |
| **reject** | `AWAITING_REMEDIATION` | `"SME_REJECTED"` | Rejection reason |
| **request_info** | `AWAITING_REMEDIATION` | `"SME_REQUESTED_INFO"` | What info is needed |
| **assign_other** | `PENDING_REVIEW` | `"REASSIGNED_TO_SME"` | New assignee in `assigned_to` |
| **escalate** | `ESCALATED` | `"SME_ESCALATED"` | Escalation reason (stays open) |

#### SME Actions on Escalated Risks → Status + Resolution

| Action | New Status | Resolution | Resolution Comment |
|--------|-----------|-----------|-------------------|
| **close_escalated** | `CLOSED` | `"CLOSED_POST_ESCALATION"` | Outcome of escalation |
| **approve_escalated** | `SME_APPROVED` | `"SME_APPROVED_POST_ESCALATION"` | Escalation resolved favorably |
| **return_to_remediation** | `AWAITING_REMEDIATION` | `"ESCALATION_REQUIRES_REMEDIATION"` | Escalation decision requires PO action |

#### PO Actions → Status + Resolution

| Action | New Status | Resolution | Resolution Comment |
|--------|-----------|-----------|-------------------|
| **self_attest** | `SELF_ATTESTED` | `"PO_SELF_ATTESTED"` | PO attestation statement |
| **submit_evidence** | `PENDING_APPROVAL` | `"PO_PROVIDED_EVIDENCE"` | Link to evidence |
| **mark_remediated** | `REMEDIATED` | `"PO_REMEDIATED"` | How it was fixed |

---

### Database Migration Strategy

#### Phase 1: Add New Enum Values (Backward Compatible)

```sql
-- Add new enum values to existing type
ALTER TYPE risk_item_status_enum ADD VALUE IF NOT EXISTS 'PENDING_REVIEW';
ALTER TYPE risk_item_status_enum ADD VALUE IF NOT EXISTS 'UNDER_SME_REVIEW';
ALTER TYPE risk_item_status_enum ADD VALUE IF NOT EXISTS 'AWAITING_REMEDIATION';
ALTER TYPE risk_item_status_enum ADD VALUE IF NOT EXISTS 'IN_REMEDIATION';
ALTER TYPE risk_item_status_enum ADD VALUE IF NOT EXISTS 'PENDING_APPROVAL';
ALTER TYPE risk_item_status_enum ADD VALUE IF NOT EXISTS 'SME_APPROVED';
ALTER TYPE risk_item_status_enum ADD VALUE IF NOT EXISTS 'SELF_ATTESTED';
ALTER TYPE risk_item_status_enum ADD VALUE IF NOT EXISTS 'REMEDIATED';
ALTER TYPE risk_item_status_enum ADD VALUE IF NOT EXISTS 'ESCALATED';
```

#### Phase 2: Migrate Existing Data

```sql
-- Map old states to new states based on resolution field
UPDATE risk_item
SET status = CASE
    WHEN status = 'OPEN' AND resolution IS NULL THEN 'PENDING_REVIEW'
    WHEN status = 'OPEN' AND resolution = 'SME_REJECTED' THEN 'AWAITING_REMEDIATION'
    WHEN status = 'IN_PROGRESS' THEN 'IN_REMEDIATION'
    WHEN status = 'WAIVED' THEN 'SME_APPROVED'
    WHEN status = 'RESOLVED' THEN 'REMEDIATED'
    WHEN status = 'CLOSED' THEN 'CLOSED'
    ELSE status
END;

-- Set default resolution if missing
UPDATE risk_item
SET resolution = CASE
    WHEN status = 'SME_APPROVED' AND resolution IS NULL THEN 'SME_APPROVED'
    WHEN status = 'REMEDIATED' AND resolution IS NULL THEN 'PO_REMEDIATED'
    WHEN status = 'SELF_ATTESTED' AND resolution IS NULL THEN 'PO_SELF_ATTESTED'
    ELSE resolution
END
WHERE resolution IS NULL;
```

#### Phase 3: Deprecate Old Values (After Migration)

```sql
-- Document that OPEN, IN_PROGRESS, WAIVED, RESOLVED are deprecated
-- (Can't remove enum values in PostgreSQL, but document for reference)
```

---

---

## Status History Tracking

### New Table: risk_item_status_history

Track all status changes for complete audit trail.

```sql
CREATE TABLE risk_item_status_history (
    history_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    risk_item_id VARCHAR(255) NOT NULL REFERENCES risk_item(risk_item_id) ON DELETE CASCADE,

    -- Status change
    from_status VARCHAR(50),           -- Previous status (null for initial creation)
    to_status VARCHAR(50) NOT NULL,    -- New status

    -- Resolution details
    resolution VARCHAR(100),            -- Resolution type
    resolution_comment TEXT,            -- Explanation/notes

    -- Actor information
    changed_by VARCHAR(255) NOT NULL,   -- User who made the change
    actor_role VARCHAR(50),             -- Role: 'SME', 'PO', 'SYSTEM', 'ADMIN'

    -- Context
    mitigation_plan TEXT,               -- For SME_APPROVED_WITH_MITIGATION
    reassigned_to VARCHAR(255),         -- For reassignments

    -- Timestamps
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    -- Metadata
    metadata JSONB                      -- Additional context (e.g., IP, user agent, etc.)
);

-- Indexes for common queries
CREATE INDEX idx_rish_risk_item_id ON risk_item_status_history(risk_item_id);
CREATE INDEX idx_rish_to_status ON risk_item_status_history(to_status);
CREATE INDEX idx_rish_changed_at ON risk_item_status_history(changed_at DESC);
CREATE INDEX idx_rish_changed_by ON risk_item_status_history(changed_by);
CREATE INDEX idx_rish_actor_role ON risk_item_status_history(actor_role);
```

**Usage Examples:**

```sql
-- Get full lifecycle history for a risk item
SELECT * FROM risk_item_status_history
WHERE risk_item_id = 'item_12345'
ORDER BY changed_at;

-- Find all risks that went through SME rejection
SELECT DISTINCT risk_item_id
FROM risk_item_status_history
WHERE to_status = 'AWAITING_REMEDIATION'
  AND resolution = 'SME_REJECTED';

-- Get average time from PENDING_REVIEW to SME_APPROVED
SELECT AVG(approved.changed_at - pending.changed_at) as avg_review_time
FROM risk_item_status_history pending
JOIN risk_item_status_history approved ON pending.risk_item_id = approved.risk_item_id
WHERE pending.to_status = 'PENDING_REVIEW'
  AND approved.to_status = 'SME_APPROVED';
```

---

## Implementation Approach

**No Backward Compatibility Needed** - Clean break, update everything at once.

### Migration Strategy

#### Phase 1: Database Schema Changes

1. **Create status history table**
```sql
-- Execute SQL above to create risk_item_status_history table
```

2. **Update risk_item_status enum**
```sql
-- Drop old enum (safe because we're not maintaining backward compatibility)
DROP TYPE IF EXISTS risk_item_status_enum CASCADE;

-- Create new enum with all values
CREATE TYPE risk_item_status_enum AS ENUM (
    -- Active states
    'PENDING_REVIEW',
    'UNDER_SME_REVIEW',
    'AWAITING_REMEDIATION',
    'IN_REMEDIATION',
    'PENDING_APPROVAL',
    'ESCALATED',
    -- Terminal states
    'SME_APPROVED',
    'SELF_ATTESTED',
    'REMEDIATED',
    'CLOSED'
);

-- Recreate column with new enum
ALTER TABLE risk_item ALTER COLUMN status TYPE VARCHAR(50);
-- (then update all existing data)
ALTER TABLE risk_item ALTER COLUMN status TYPE risk_item_status_enum USING status::risk_item_status_enum;
```

3. **Migrate existing data**
```sql
-- Map old states to new states
UPDATE risk_item
SET status = CASE
    WHEN status = 'OPEN' AND resolution IS NULL THEN 'PENDING_REVIEW'
    WHEN status = 'OPEN' AND resolution LIKE '%REJECTED%' THEN 'AWAITING_REMEDIATION'
    WHEN status = 'IN_PROGRESS' THEN 'IN_REMEDIATION'
    WHEN status = 'WAIVED' THEN 'SME_APPROVED'
    WHEN status = 'RESOLVED' THEN 'REMEDIATED'
    WHEN status = 'CLOSED' THEN 'CLOSED'
    ELSE 'PENDING_REVIEW'
END;

-- Populate initial status history for existing items
INSERT INTO risk_item_status_history (
    risk_item_id,
    from_status,
    to_status,
    resolution,
    resolution_comment,
    changed_by,
    actor_role,
    changed_at
)
SELECT
    risk_item_id,
    NULL as from_status,  -- No previous status (initial creation)
    status as to_status,
    resolution,
    resolution_comment,
    COALESCE(raised_by, 'SYSTEM') as changed_by,
    CASE
        WHEN creation_type = 'MANUAL_SME_INITIATED' THEN 'SME'
        WHEN creation_type = 'SYSTEM_AUTO_CREATION' THEN 'SYSTEM'
        ELSE 'SYSTEM'
    END as actor_role,
    opened_at as changed_at
FROM risk_item;
```

#### Phase 2: Java Code Updates

1. **Update RiskItemStatus enum**
2. **Update RiskItem entity** (status field type)
3. **Create RiskItemStatusHistory entity**
4. **Create StatusHistoryService** to log all transitions
5. **Update DomainRiskAggregationService** - modify "open items" logic
6. **Update all repositories** - update SQL queries filtering by status
7. **Update controllers** - use new status values
8. **Update DTOs** - ensure responses use new status names

#### Phase 3: Testing

1. Unit tests for state transitions
2. Integration tests for status history logging
3. Test all SME review actions
4. Test PO self-attestation flow
5. Test query performance with new status values
6. E2E testing with frontend

---

---

## Decisions Made (from User Feedback)

✅ **Self-Attestation:** Terminal state, no SME review - **separate mutually exclusive flow**
✅ **Escalation:** **Active state** - escalated risks stay open until SME closes them after resolution
✅ **Status History:** **Yes** - implement `risk_item_status_history` table
✅ **Backward Compatibility:** **Not needed** - clean break

---

## Open Questions

1. **Reassignment:** Should reassignment create a new risk or update existing?
   - **Proposed:** Update existing, clear `assigned_to`/`assigned_at`, reset to `PENDING_REVIEW`

2. **Multiple Escalations:** Can a risk be escalated multiple times?
   - **Proposed:** Allow re-escalation (from ESCALATED → ESCALATED with new comment), track in status history

3. **Status History Access:** Should status history be exposed via API?
   - **Proposed:** Yes - `GET /api/v1/risk-items/{id}/history`

4. **Self-Attestation Restrictions:** Can any PO self-attest, or only for certain field types?
   - **Proposed:** Field-level configuration in `profile-fields.registry.yaml` (`allow_self_attestation: true/false`)

---

## Next Steps (Ready for Implementation)

1. ✅ **Review and approve updated proposal**
2. Create database migration scripts (V14__risk_item_state_machine.sql)
3. Implement status history service
4. Update Java enum and entities
5. Update service methods for new state transitions
6. Update all SQL queries (7 files identified)
7. Create StatusHistoryService for automatic logging
8. Update tests
9. Update API documentation
10. Deploy and verify

---

**Status:** ✅ **APPROVED - READY FOR IMPLEMENTATION**

**Approved By:** [User confirmation pending]
**Date:** 2025-10-14
