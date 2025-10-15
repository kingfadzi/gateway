-- V14: Risk Item State Machine Implementation
-- Implements new 10-state workflow with status history tracking
-- Two mutually exclusive flows: Self-Attestation vs SME Assessment

-- =============================================================================
-- Part 1: Create Status History Table
-- =============================================================================

CREATE TABLE risk_item_status_history (
    history_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    risk_item_id VARCHAR(255) NOT NULL REFERENCES risk_item(risk_item_id) ON DELETE CASCADE,

    -- Status change
    from_status VARCHAR(50),           -- Previous status (null for initial creation)
    to_status VARCHAR(50) NOT NULL,    -- New status

    -- Resolution details
    resolution VARCHAR(100),            -- Resolution type (e.g., SME_APPROVED, SME_REJECTED)
    resolution_comment TEXT,            -- Explanation/notes

    -- Actor information
    changed_by VARCHAR(255) NOT NULL,   -- User who made the change
    actor_role VARCHAR(50),             -- Role: 'SME', 'PO', 'SYSTEM', 'ADMIN'

    -- Context
    mitigation_plan TEXT,               -- For SME_APPROVED_WITH_MITIGATION
    reassigned_to VARCHAR(255),         -- For reassignments

    -- Timestamps
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    -- Metadata
    metadata JSONB                      -- Additional context (e.g., IP, escalation details, etc.)
);

-- Indexes for common queries
CREATE INDEX idx_rish_risk_item_id ON risk_item_status_history(risk_item_id);
CREATE INDEX idx_rish_to_status ON risk_item_status_history(to_status);
CREATE INDEX idx_rish_changed_at ON risk_item_status_history(changed_at DESC);
CREATE INDEX idx_rish_changed_by ON risk_item_status_history(changed_by);
CREATE INDEX idx_rish_actor_role ON risk_item_status_history(actor_role);
CREATE INDEX idx_rish_resolution ON risk_item_status_history(resolution);

COMMENT ON TABLE risk_item_status_history IS 'Complete audit trail of all status changes for risk items';
COMMENT ON COLUMN risk_item_status_history.from_status IS 'Previous status (null for initial creation)';
COMMENT ON COLUMN risk_item_status_history.to_status IS 'New status after transition';
COMMENT ON COLUMN risk_item_status_history.resolution IS 'Specific outcome: SME_APPROVED, SME_REJECTED, PO_SELF_ATTESTED, etc.';
COMMENT ON COLUMN risk_item_status_history.actor_role IS 'Role of user making change: SME, PO, SYSTEM, ADMIN';
COMMENT ON COLUMN risk_item_status_history.mitigation_plan IS 'Required mitigation plan for SME_APPROVED_WITH_MITIGATION';

-- =============================================================================
-- Part 2: Update Risk Item Status Enum
-- =============================================================================

-- Drop dependent view before altering column type
DROP VIEW IF EXISTS v_risk_item_missing_profile_field;

-- Convert status column to VARCHAR temporarily
ALTER TABLE risk_item ALTER COLUMN status TYPE VARCHAR(50);

-- Drop old enum type
DROP TYPE IF EXISTS risk_item_status_enum CASCADE;

-- Note: Using VARCHAR instead of custom enum type for JPA/Hibernate compatibility
-- CHECK constraint provides database-level type safety without JPA comparison issues

-- =============================================================================
-- Part 3: Migrate Existing Data to New States
-- =============================================================================

-- Map old states to new states based on current status and resolution
UPDATE risk_item
SET status = CASE
    -- OPEN → PENDING_REVIEW (default initial state)
    WHEN status = 'OPEN' AND (resolution IS NULL OR resolution = '') THEN 'PENDING_REVIEW'

    -- OPEN with rejection → AWAITING_REMEDIATION
    WHEN status = 'OPEN' AND (
        resolution LIKE '%REJECT%' OR
        resolution LIKE '%INFO_REQUEST%' OR
        resolution = 'SME_REJECTED' OR
        resolution = 'SME_REQUESTED_INFO'
    ) THEN 'AWAITING_REMEDIATION'

    -- IN_PROGRESS → IN_REMEDIATION or UNDER_SME_REVIEW
    WHEN status = 'IN_PROGRESS' AND assigned_to IS NOT NULL THEN 'UNDER_SME_REVIEW'
    WHEN status = 'IN_PROGRESS' THEN 'IN_REMEDIATION'

    -- WAIVED → SME_APPROVED
    WHEN status = 'WAIVED' THEN 'SME_APPROVED'

    -- RESOLVED → REMEDIATED
    WHEN status = 'RESOLVED' THEN 'REMEDIATED'

    -- CLOSED → CLOSED (no change)
    WHEN status = 'CLOSED' THEN 'CLOSED'

    -- Default fallback
    ELSE 'PENDING_REVIEW'
END;

-- Set default resolution if missing for terminal states
UPDATE risk_item
SET resolution = CASE
    WHEN status = 'SME_APPROVED' AND (resolution IS NULL OR resolution = '') THEN 'SME_APPROVED'
    WHEN status = 'REMEDIATED' AND (resolution IS NULL OR resolution = '') THEN 'PO_REMEDIATED'
    WHEN status = 'SELF_ATTESTED' AND (resolution IS NULL OR resolution = '') THEN 'PO_SELF_ATTESTED'
    WHEN status = 'CLOSED' AND (resolution IS NULL OR resolution = '') THEN 'ADMIN_CLOSED'
    ELSE resolution
END
WHERE status IN ('SME_APPROVED', 'REMEDIATED', 'SELF_ATTESTED', 'CLOSED')
  AND (resolution IS NULL OR resolution = '');

-- =============================================================================
-- Part 4: Populate Initial Status History
-- =============================================================================

-- Create initial history record for all existing risk items
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
    NULL as from_status,                    -- No previous status (initial creation)
    status as to_status,
    resolution,
    resolution_comment,
    COALESCE(raised_by, 'SYSTEM') as changed_by,
    CASE
        WHEN creation_type = 'MANUAL_SME_INITIATED' THEN 'SME'
        WHEN creation_type = 'MANUAL_CREATION' THEN 'SME'
        WHEN creation_type = 'SYSTEM_AUTO_CREATION' THEN 'SYSTEM'
        ELSE 'SYSTEM'
    END as actor_role,
    opened_at as changed_at
FROM risk_item;

-- =============================================================================
-- Part 5: Add CHECK Constraint for Status Values
-- =============================================================================

-- Add CHECK constraint to enforce valid status values (provides type safety without enum issues)
ALTER TABLE risk_item ADD CONSTRAINT chk_risk_item_status
CHECK (status IN (
    'PENDING_REVIEW',
    'UNDER_SME_REVIEW',
    'AWAITING_REMEDIATION',
    'IN_REMEDIATION',
    'PENDING_APPROVAL',
    'ESCALATED',
    'SME_APPROVED',
    'SELF_ATTESTED',
    'REMEDIATED',
    'CLOSED'
));

-- Add comment explaining the states
COMMENT ON COLUMN risk_item.status IS 'Risk item status: Active states (PENDING_REVIEW, UNDER_SME_REVIEW, AWAITING_REMEDIATION, IN_REMEDIATION, PENDING_APPROVAL, ESCALATED) or Terminal states (SME_APPROVED, SELF_ATTESTED, REMEDIATED, CLOSED)';

-- Set default for new records
ALTER TABLE risk_item ALTER COLUMN status SET DEFAULT 'PENDING_REVIEW';

-- =============================================================================
-- Part 6: Recreate Dependent View
-- =============================================================================

-- Recreate the diagnostic view that was dropped earlier
CREATE OR REPLACE VIEW v_risk_item_missing_profile_field AS
SELECT
    ri.risk_item_id,
    ri.app_id,
    ri.field_key,
    ri.triggering_evidence_id,
    ri.status,
    ri.created_at,
    CASE
        WHEN p.profile_id IS NULL THEN 'No profile exists for app'
        WHEN pf.id IS NULL THEN 'Profile exists but field not in profile'
        ELSE 'Unknown issue'
    END AS issue_reason
FROM risk_item ri
LEFT JOIN profile p ON p.app_id = ri.app_id
LEFT JOIN profile_field pf ON pf.profile_id = p.profile_id AND pf.field_key = ri.field_key
WHERE ri.profile_field_id IS NULL;

COMMENT ON VIEW v_risk_item_missing_profile_field IS
'Diagnostic view showing risk items still missing profile_field_id after backfill';

-- =============================================================================
-- Part 7: Add Trigger for Updated_At on Status History
-- =============================================================================

-- Update timestamp trigger function (if not already exists)
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger for status history table
CREATE TRIGGER update_risk_item_status_history_updated_at
    BEFORE UPDATE ON risk_item_status_history
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- =============================================================================
-- Verification Queries (for manual testing)
-- =============================================================================

-- Verify status distribution after migration
-- SELECT status, COUNT(*) as count
-- FROM risk_item
-- GROUP BY status
-- ORDER BY count DESC;

-- Verify status history was populated
-- SELECT COUNT(*) as total_history_records
-- FROM risk_item_status_history;

-- Check for any null resolutions in terminal states
-- SELECT risk_item_id, status, resolution
-- FROM risk_item
-- WHERE status IN ('SME_APPROVED', 'REMEDIATED', 'SELF_ATTESTED', 'CLOSED')
--   AND (resolution IS NULL OR resolution = '');
