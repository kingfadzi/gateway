-- V15: Revert Status Column from Enum to VARCHAR
-- Fixes JPA/Hibernate compatibility issues with PostgreSQL custom enum types

-- Drop indexes that reference status column
DROP INDEX IF EXISTS idx_risk_item_status;
DROP INDEX IF EXISTS idx_risk_item_assigned_to_status;
DROP INDEX IF EXISTS idx_risk_item_arb_status;
DROP INDEX IF EXISTS idx_risk_item_rating_dimension_status;

-- Drop dependent view before altering column type
DROP VIEW IF EXISTS v_risk_item_missing_profile_field;

-- Convert status column from enum to VARCHAR
ALTER TABLE risk_item ALTER COLUMN status TYPE VARCHAR(50);

-- Drop the enum type
DROP TYPE IF EXISTS risk_item_status_enum CASCADE;

-- Add CHECK constraint for type safety
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

-- Recreate indexes on status column
CREATE INDEX idx_risk_item_status ON risk_item(status);
CREATE INDEX idx_risk_item_assigned_to_status ON risk_item(assigned_to, status) WHERE assigned_to IS NOT NULL;
CREATE INDEX idx_risk_item_arb_status ON risk_item(arb, status);
CREATE INDEX idx_risk_item_rating_dimension_status ON risk_item(risk_rating_dimension, status);

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
