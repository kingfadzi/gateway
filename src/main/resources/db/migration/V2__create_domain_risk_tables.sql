-- Migration V2: Create domain_risk and risk_item tables for risk aggregation feature
-- See: RISK_AGGREGATION_PLAN.md

-- Create domain_risk table (aggregated risks per domain per application)
CREATE TABLE domain_risk (
    domain_risk_id VARCHAR(255) PRIMARY KEY,
    app_id VARCHAR(255) NOT NULL,
    domain VARCHAR(100) NOT NULL,           -- security, integrity, availability, etc.
    derived_from VARCHAR(100) NOT NULL,     -- security_rating, integrity_rating, etc.
    arb VARCHAR(100) NOT NULL,              -- security_arb, integrity_arb, etc.

    -- Aggregated metadata
    title VARCHAR(500),
    description TEXT,
    total_items INTEGER DEFAULT 0,
    open_items INTEGER DEFAULT 0,
    high_priority_items INTEGER DEFAULT 0,

    -- Priority & severity (calculated from items)
    overall_priority VARCHAR(50),           -- CRITICAL, HIGH, MEDIUM, LOW
    overall_severity VARCHAR(50),           -- high, medium, low
    priority_score INTEGER,                 -- Calculated: 0-100

    -- Status
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING_ARB_REVIEW',

    -- Assignment
    assigned_arb VARCHAR(100),
    assigned_at TIMESTAMP WITH TIME ZONE,

    -- Lifecycle
    opened_at TIMESTAMP WITH TIME ZONE NOT NULL,
    closed_at TIMESTAMP WITH TIME ZONE,
    last_item_added_at TIMESTAMP WITH TIME ZONE,

    -- Audit
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    -- Unique constraint: one domain risk per domain per app
    CONSTRAINT uk_app_domain UNIQUE (app_id, domain)
);

-- Create risk_item table (individual evidence-level risks)
CREATE TABLE risk_item (
    risk_item_id VARCHAR(255) PRIMARY KEY,
    domain_risk_id VARCHAR(255) NOT NULL,

    -- References
    app_id VARCHAR(255) NOT NULL,
    field_key VARCHAR(255) NOT NULL,
    profile_field_id VARCHAR(255),
    triggering_evidence_id VARCHAR(255),
    track_id VARCHAR(255),

    -- Content
    title VARCHAR(500),
    description TEXT,

    -- Priority & severity
    priority VARCHAR(50),                   -- CRITICAL, HIGH, MEDIUM, LOW (from registry)
    severity VARCHAR(50),                   -- high, medium, low (from evidence status)
    priority_score INTEGER,                 -- Calculated score 0-100
    evidence_status VARCHAR(50),            -- missing, expiring, expired, rejected

    -- Status
    status VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    resolution VARCHAR(50),
    resolution_comment TEXT,

    -- Lifecycle
    creation_type VARCHAR(50),
    raised_by VARCHAR(255),
    opened_at TIMESTAMP WITH TIME ZONE NOT NULL,
    resolved_at TIMESTAMP WITH TIME ZONE,

    -- Snapshot
    policy_requirement_snapshot JSONB,

    -- Audit
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (domain_risk_id) REFERENCES domain_risk(domain_risk_id) ON DELETE CASCADE
);

-- Create indexes for domain_risk
CREATE INDEX idx_domain_risk_app ON domain_risk(app_id);
CREATE INDEX idx_domain_risk_arb ON domain_risk(assigned_arb);
CREATE INDEX idx_domain_risk_status ON domain_risk(status);
CREATE INDEX idx_domain_risk_priority ON domain_risk(priority_score DESC);
CREATE INDEX idx_domain_risk_domain ON domain_risk(domain);

-- Create indexes for risk_item
CREATE INDEX idx_risk_item_domain_risk ON risk_item(domain_risk_id);
CREATE INDEX idx_risk_item_app ON risk_item(app_id);
CREATE INDEX idx_risk_item_field ON risk_item(field_key);
CREATE INDEX idx_risk_item_evidence ON risk_item(triggering_evidence_id);
CREATE INDEX idx_risk_item_priority ON risk_item(priority_score DESC);
CREATE INDEX idx_risk_item_status ON risk_item(status);

-- Create trigger to update updated_at timestamp for domain_risk
CREATE OR REPLACE FUNCTION update_domain_risk_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_domain_risk_updated_at
    BEFORE UPDATE ON domain_risk
    FOR EACH ROW
    EXECUTE FUNCTION update_domain_risk_updated_at();

-- Create trigger to update updated_at timestamp for risk_item
CREATE OR REPLACE FUNCTION update_risk_item_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_risk_item_updated_at
    BEFORE UPDATE ON risk_item
    FOR EACH ROW
    EXECUTE FUNCTION update_risk_item_updated_at();
