-- V3: Risk Comment Table and Lifecycle Enhancements
-- Purpose: Support comments/collaboration and manual risk creation

-- =============================================================================
-- Risk Comment Table (for collaboration on risk items)
-- =============================================================================
CREATE TABLE risk_comment (
    comment_id VARCHAR(255) PRIMARY KEY,
    risk_item_id VARCHAR(255) NOT NULL,
    comment_type VARCHAR(50) NOT NULL, -- GENERAL, STATUS_CHANGE, REVIEW, RESOLUTION
    comment_text TEXT NOT NULL,
    commented_by VARCHAR(255) NOT NULL,
    commented_at TIMESTAMP WITH TIME ZONE NOT NULL,

    -- Metadata
    is_internal BOOLEAN DEFAULT FALSE, -- Internal ARB notes vs visible to PO
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_risk_comment_risk_item
        FOREIGN KEY (risk_item_id)
        REFERENCES risk_item(risk_item_id)
        ON DELETE CASCADE
);

-- Indexes for efficient comment queries
CREATE INDEX idx_risk_comment_risk_item_id ON risk_comment(risk_item_id);
CREATE INDEX idx_risk_comment_commented_at ON risk_comment(commented_at DESC);
CREATE INDEX idx_risk_comment_commented_by ON risk_comment(commented_by);
CREATE INDEX idx_risk_comment_type ON risk_comment(comment_type);

-- Create trigger function to update updated_at timestamp for risk_comment
CREATE OR REPLACE FUNCTION update_risk_comment_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Auto-update trigger for updated_at
CREATE TRIGGER tr_risk_comment_updated_at
    BEFORE UPDATE ON risk_comment
    FOR EACH ROW
    EXECUTE FUNCTION update_risk_comment_updated_at();

-- =============================================================================
-- Comments for Documentation
-- =============================================================================
COMMENT ON TABLE risk_comment IS 'Comments and discussion thread for risk items';
COMMENT ON COLUMN risk_comment.comment_type IS 'Type: GENERAL, STATUS_CHANGE, REVIEW, RESOLUTION';
COMMENT ON COLUMN risk_comment.is_internal IS 'TRUE for internal ARB notes, FALSE for PO-visible';
