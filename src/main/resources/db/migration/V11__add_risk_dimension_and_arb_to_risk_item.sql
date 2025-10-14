-- ============================================================================
-- V11: Add risk_dimension and arb columns to risk_item table
-- ============================================================================
-- Purpose: Denormalize risk_dimension and arb from profile_field for:
--   - Faster queries (no JOIN required)
--   - Simpler API responses
--   - Better indexing capabilities
-- ============================================================================

-- Add denormalized columns to risk_item
ALTER TABLE risk_item
    ADD COLUMN IF NOT EXISTS risk_dimension VARCHAR(100),
    ADD COLUMN IF NOT EXISTS arb VARCHAR(100);

-- Backfill risk_dimension and arb by joining through profile_field
-- Note: This uses DISTINCT ON to handle cases where multiple profile_fields
-- might exist for the same field_key (picks first match)
UPDATE risk_item ri
SET
    risk_dimension = CASE
        -- Strip '_rating' suffix from derived_from to get risk_dimension
        -- Examples: security_rating → security, availability_rating → availability
        WHEN pf.derived_from LIKE '%_rating'
        THEN REPLACE(pf.derived_from, '_rating', '')
        -- For non-rating derived_from (e.g., app_criticality_assessment)
        ELSE pf.derived_from
    END,
    arb = pf.arb
FROM (
    SELECT DISTINCT ON (field_key) field_key, derived_from, arb
    FROM profile_field
    WHERE derived_from IS NOT NULL
) pf
WHERE ri.field_key = pf.field_key;

-- Add indexes for efficient filtering
CREATE INDEX IF NOT EXISTS idx_risk_item_risk_dimension
    ON risk_item(risk_dimension);

CREATE INDEX IF NOT EXISTS idx_risk_item_arb
    ON risk_item(arb);

CREATE INDEX IF NOT EXISTS idx_risk_item_arb_status
    ON risk_item(arb, status);

CREATE INDEX IF NOT EXISTS idx_risk_item_dimension_status
    ON risk_item(risk_dimension, status);

-- Add comments
COMMENT ON COLUMN risk_item.risk_dimension IS
    'Risk dimension (security, confidentiality, integrity, availability, resilience, app_criticality_assessment) - denormalized from profile_field.derived_from';

COMMENT ON COLUMN risk_item.arb IS
    'Architecture Review Board assignment (data, security, operations, enterprise_architecture) - denormalized from profile_field.arb';

-- Verification and logging
DO $$
DECLARE
    total_items INT;
    updated_items INT;
    null_dimension_items INT;
    null_arb_items INT;
    orphaned_items INT;
    rec RECORD;
BEGIN
    -- Count totals
    SELECT COUNT(*) INTO total_items FROM risk_item;
    SELECT COUNT(*) INTO updated_items
        FROM risk_item
        WHERE risk_dimension IS NOT NULL AND arb IS NOT NULL;
    SELECT COUNT(*) INTO null_dimension_items
        FROM risk_item
        WHERE risk_dimension IS NULL;
    SELECT COUNT(*) INTO null_arb_items
        FROM risk_item
        WHERE arb IS NULL;

    -- Find orphaned records (field_key doesn't match any profile_field)
    SELECT COUNT(*) INTO orphaned_items
    FROM risk_item ri
    LEFT JOIN profile_field pf ON ri.field_key = pf.field_key
    WHERE pf.field_key IS NULL;

    RAISE NOTICE '================================================';
    RAISE NOTICE 'Risk Item Backfill Complete';
    RAISE NOTICE '================================================';
    RAISE NOTICE '  Total Risk Items: %', total_items;
    RAISE NOTICE '  Successfully Backfilled: % (%.1f%%)',
        updated_items,
        (updated_items::FLOAT / NULLIF(total_items, 0) * 100);
    RAISE NOTICE '  NULL risk_dimension: %', null_dimension_items;
    RAISE NOTICE '  NULL arb: %', null_arb_items;
    RAISE NOTICE '  Orphaned (no profile_field match): %', orphaned_items;
    RAISE NOTICE '================================================';

    -- Warn if there are orphaned records
    IF null_dimension_items > 0 OR null_arb_items > 0 THEN
        RAISE WARNING 'Some risk_items could not be backfilled - check field_key mapping to profile_field!';

        -- Show sample of unmapped field_keys
        RAISE NOTICE 'Sample unmapped field_keys:';
        FOR rec IN (
            SELECT DISTINCT field_key
            FROM risk_item
            WHERE risk_dimension IS NULL OR arb IS NULL
            LIMIT 5
        ) LOOP
            RAISE NOTICE '  - %', rec.field_key;
        END LOOP;
    END IF;
END $$;

-- Display risk_dimension distribution
DO $$
DECLARE
    rec RECORD;
BEGIN
    RAISE NOTICE '';
    RAISE NOTICE 'Risk Dimension Distribution:';
    RAISE NOTICE '----------------------------';
    FOR rec IN (
        SELECT
            COALESCE(risk_dimension, '(null)') as dimension,
            COALESCE(arb, '(null)') as arb_name,
            COUNT(*) as count
        FROM risk_item
        GROUP BY risk_dimension, arb
        ORDER BY COUNT(*) DESC
    ) LOOP
        RAISE NOTICE '  % / % : % items', rec.dimension, rec.arb_name, rec.count;
    END LOOP;
END $$;
