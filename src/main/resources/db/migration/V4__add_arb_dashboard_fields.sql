-- Migration V3: Add ARB Dashboard fields for user-level assignment
-- Supports scope-based filtering: my-queue, my-domain, all-domains
-- See: ARB_DASHBOARD_BACKEND_SPEC.md

-- Add user-level assignment to domain_risk
ALTER TABLE domain_risk ADD COLUMN assigned_to VARCHAR(255);
ALTER TABLE domain_risk ADD COLUMN assigned_to_name VARCHAR(255);

-- Create index for efficient my-queue queries
CREATE INDEX idx_domain_risk_assigned_to ON domain_risk(assigned_to);

-- Note: transactionCycle (business_unit) already exists in application table
-- Note: appCriticalityAssessment already exists in application table
