-- V16: Fix Status History ID Type
-- Changes history_id from UUID to VARCHAR for consistency with other ID fields

-- Convert history_id from UUID to VARCHAR
ALTER TABLE risk_item_status_history ALTER COLUMN history_id TYPE VARCHAR(255);

-- Update the default to generate string IDs (using UUID format but as string)
ALTER TABLE risk_item_status_history ALTER COLUMN history_id SET DEFAULT ('history_' || gen_random_uuid()::text);
