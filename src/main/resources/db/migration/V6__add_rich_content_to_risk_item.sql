-- ============================================================================
-- V6: Add rich content columns to risk_item table
-- ============================================================================
-- Purpose: Support manual SME-initiated risk creation with rich narrative
-- Preserves hypothesis/condition/consequence structure from risk_story
-- ============================================================================

-- Add rich content columns
ALTER TABLE risk_item
    ADD COLUMN IF NOT EXISTS hypothesis TEXT,
    ADD COLUMN IF NOT EXISTS condition TEXT,
    ADD COLUMN IF NOT EXISTS consequence TEXT,
    ADD COLUMN IF NOT EXISTS control_refs TEXT;

-- Add index for full-text search on risk content
CREATE INDEX IF NOT EXISTS idx_risk_item_content_search
    ON risk_item USING gin(
        to_tsvector('english',
            COALESCE(title, '') || ' ' ||
            COALESCE(description, '') || ' ' ||
            COALESCE(hypothesis, '') || ' ' ||
            COALESCE(condition, '') || ' ' ||
            COALESCE(consequence, '')
        )
    );

-- Add comment explaining the columns
COMMENT ON COLUMN risk_item.hypothesis IS 'Narrative hypothesis: "If X happens..." - describes the risk scenario';
COMMENT ON COLUMN risk_item.condition IS 'Condition under which risk manifests: "When Y exists..." - preconditions';
COMMENT ON COLUMN risk_item.consequence IS 'Impact/consequence: "Then Z occurs..." - what happens if risk materializes';
COMMENT ON COLUMN risk_item.control_refs IS 'References to relevant controls or mitigations';
