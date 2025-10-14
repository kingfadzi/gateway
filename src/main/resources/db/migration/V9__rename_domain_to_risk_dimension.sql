-- Rename domain column to risk_dimension for clarity
-- CIA+R+S+Governance are "risk dimensions" not "domains"

-- Rename the column
ALTER TABLE domain_risk
    RENAME COLUMN domain TO risk_dimension;

-- Update unique constraint (app_id, domain) -> (app_id, risk_dimension)
ALTER TABLE domain_risk
    DROP CONSTRAINT IF EXISTS domain_risk_app_domain_unique;

ALTER TABLE domain_risk
    ADD CONSTRAINT domain_risk_app_risk_dimension_unique UNIQUE (app_id, risk_dimension);

-- Drop old domain indexes
DROP INDEX IF EXISTS idx_domain_risk_domain;
DROP INDEX IF EXISTS idx_domain_risk_domain_status;

-- Create new risk_dimension indexes
CREATE INDEX idx_domain_risk_risk_dimension ON domain_risk(risk_dimension);
CREATE INDEX idx_domain_risk_risk_dimension_status ON domain_risk(risk_dimension, status);

-- Update comments
COMMENT ON COLUMN domain_risk.risk_dimension IS 'Risk dimension (CIA+R+S+Governance): security, integrity, confidentiality, availability, resilience, app_criticality_assessment';
