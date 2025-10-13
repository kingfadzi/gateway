-- ============================================================================
-- V8: Add assignment fields to risk_item
-- ============================================================================
-- Purpose: Enable individual SME assignment at risk item level
-- - Risk items can be assigned to specific SMEs
-- - Multiple SMEs can work on same app simultaneously
-- - Supports self-assignment and re-assignment workflows
-- - Domain-level ARB assignment remains for strategic oversight
-- ============================================================================

-- Step 1: Add assignment columns to risk_item
-- ============================================================================
ALTER TABLE risk_item
    ADD COLUMN IF NOT EXISTS assigned_to TEXT,
    ADD COLUMN IF NOT EXISTS assigned_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS assigned_by TEXT;

-- Step 2: Add indexes for assignment queries
-- ============================================================================

-- Index for "My Queue" queries (assigned risks by user)
CREATE INDEX IF NOT EXISTS idx_risk_item_assigned_to
    ON risk_item(assigned_to)
    WHERE assigned_to IS NOT NULL;

-- Index for assigned risks by user and status
CREATE INDEX IF NOT EXISTS idx_risk_item_assigned_to_status
    ON risk_item(assigned_to, status)
    WHERE assigned_to IS NOT NULL;

-- Index for unassigned risk pool
CREATE INDEX IF NOT EXISTS idx_risk_item_unassigned
    ON risk_item(status)
    WHERE assigned_to IS NULL AND status IN ('OPEN', 'IN_PROGRESS');

-- Composite index for "My Queue" app drill-down
CREATE INDEX IF NOT EXISTS idx_risk_item_assigned_to_app
    ON risk_item(assigned_to, app_id, priority_score DESC)
    WHERE assigned_to IS NOT NULL AND status IN ('OPEN', 'IN_PROGRESS');

-- Step 3: Add comments
-- ============================================================================
COMMENT ON COLUMN risk_item.assigned_to IS 'Email or user ID of assigned SME (individual work assignment)';
COMMENT ON COLUMN risk_item.assigned_at IS 'Timestamp when risk was assigned to current assignee';
COMMENT ON COLUMN risk_item.assigned_by IS 'Email or user ID who made the assignment (self or manager)';

-- Step 4: Create assignment history table for audit trail
-- ============================================================================
CREATE TABLE IF NOT EXISTS risk_item_assignment_history (
    history_id TEXT PRIMARY KEY,
    risk_item_id TEXT NOT NULL,

    -- Assignment details
    assigned_to TEXT,
    assigned_from TEXT,  -- Previous assignee (null if first assignment)
    assigned_by TEXT NOT NULL,
    assignment_type TEXT NOT NULL,

    -- Context
    reason TEXT,

    -- Timestamps
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Foreign key
    CONSTRAINT fk_assignment_history_risk_item
        FOREIGN KEY (risk_item_id)
        REFERENCES risk_item(risk_item_id)
        ON DELETE CASCADE,

    -- Constraints
    CONSTRAINT chk_assignment_type CHECK (
        assignment_type IN ('SELF_ASSIGN', 'MANUAL_ASSIGN', 'AUTO_ASSIGN', 'UNASSIGN')
    )
);

-- Step 5: Add indexes for assignment history queries
-- ============================================================================

-- Index for history by risk item
CREATE INDEX IF NOT EXISTS idx_assignment_history_risk_item
    ON risk_item_assignment_history(risk_item_id, assigned_at DESC);

-- Index for user assignment history
CREATE INDEX IF NOT EXISTS idx_assignment_history_assigned_to
    ON risk_item_assignment_history(assigned_to, assigned_at DESC);

-- Index for recent assignments
CREATE INDEX IF NOT EXISTS idx_assignment_history_assigned_at
    ON risk_item_assignment_history(assigned_at DESC);

-- Step 6: Add comments to history table
-- ============================================================================
COMMENT ON TABLE risk_item_assignment_history IS
    'Audit trail of risk item assignment changes. Tracks who assigned what to whom and when.';

COMMENT ON COLUMN risk_item_assignment_history.assigned_to IS
    'User who received the assignment (null for unassignment)';

COMMENT ON COLUMN risk_item_assignment_history.assigned_from IS
    'Previous assignee (null if first assignment or was unassigned)';

COMMENT ON COLUMN risk_item_assignment_history.assigned_by IS
    'User who performed the assignment action';

COMMENT ON COLUMN risk_item_assignment_history.assignment_type IS
    'Type: SELF_ASSIGN (user assigned to self), MANUAL_ASSIGN (assigned by another), AUTO_ASSIGN (system), UNASSIGN (returned to pool)';

-- Step 7: Log migration results
-- ============================================================================
DO $$
BEGIN
    RAISE NOTICE '============================================';
    RAISE NOTICE 'Risk Item Assignment Migration Complete';
    RAISE NOTICE '============================================';
    RAISE NOTICE 'Added columns: assigned_to, assigned_at, assigned_by';
    RAISE NOTICE 'Created table: risk_item_assignment_history';
    RAISE NOTICE 'Created 6 indexes for assignment queries';
    RAISE NOTICE '============================================';
    RAISE NOTICE 'Next steps:';
    RAISE NOTICE '  1. Deploy backend code with assignment endpoints';
    RAISE NOTICE '  2. Update frontend to support "My Queue" view';
    RAISE NOTICE '  3. Optionally migrate old assigned_sme data if needed';
    RAISE NOTICE '============================================';
END $$;
