-- ============================================================================
-- V12: Rename risk_dimension to risk_rating_dimension and use full derived_from
-- ============================================================================
-- Purpose: Eliminate ambiguity between ARB names and dimension names
--   Before: risk_dimension="security", arb="security" (confusing!)
--   After:  risk_rating_dimension="security_rating", arb="security" (clear!)
--
-- Architecture model:
--   ARB → oversees → CIA+S+R Rating Dimensions → contain → Fields → raise → Risk Items
-- ============================================================================

-- ============================================
-- PART 1: Update risk_item table
-- ============================================

-- Step 1: Add new column
ALTER TABLE risk_item
    ADD COLUMN IF NOT EXISTS risk_rating_dimension VARCHAR(100);

-- Step 2: Backfill with full derived_from values (add _rating suffix)
UPDATE risk_item
SET risk_rating_dimension = CASE
    -- Add _rating suffix to CIA+S+R dimensions
    WHEN risk_dimension = 'security' THEN 'security_rating'
    WHEN risk_dimension = 'confidentiality' THEN 'confidentiality_rating'
    WHEN risk_dimension = 'integrity' THEN 'integrity_rating'
    WHEN risk_dimension = 'availability' THEN 'availability_rating'
    WHEN risk_dimension = 'resilience' THEN 'resilience_rating'
    -- Keep app_criticality_assessment as-is (no _rating suffix)
    WHEN risk_dimension = 'app_criticality_assessment' THEN 'app_criticality_assessment'
    -- Handle any NULLs or unexpected values
    ELSE risk_dimension
END
WHERE risk_dimension IS NOT NULL;

-- Step 3: Drop old indexes
DROP INDEX IF EXISTS idx_risk_item_risk_dimension;
DROP INDEX IF EXISTS idx_risk_item_dimension_status;

-- Step 4: Create new indexes
CREATE INDEX IF NOT EXISTS idx_risk_item_risk_rating_dimension
    ON risk_item(risk_rating_dimension);

CREATE INDEX IF NOT EXISTS idx_risk_item_rating_dimension_status
    ON risk_item(risk_rating_dimension, status);

-- Step 5: Drop old column
ALTER TABLE risk_item
    DROP COLUMN IF EXISTS risk_dimension;

-- Step 6: Add comment
COMMENT ON COLUMN risk_item.risk_rating_dimension IS
    'Full risk rating dimension from derived_from (e.g., security_rating, availability_rating, app_criticality_assessment)';

-- ============================================
-- PART 2: Update domain_risk table
-- ============================================

-- Step 1: Add new column
ALTER TABLE domain_risk
    ADD COLUMN IF NOT EXISTS risk_rating_dimension VARCHAR(100);

-- Step 2: Backfill with full derived_from values (add _rating suffix)
UPDATE domain_risk
SET risk_rating_dimension = CASE
    -- Add _rating suffix to CIA+S+R dimensions
    WHEN risk_dimension = 'security' THEN 'security_rating'
    WHEN risk_dimension = 'confidentiality' THEN 'confidentiality_rating'
    WHEN risk_dimension = 'integrity' THEN 'integrity_rating'
    WHEN risk_dimension = 'availability' THEN 'availability_rating'
    WHEN risk_dimension = 'resilience' THEN 'resilience_rating'
    -- Keep app_criticality_assessment as-is (no _rating suffix)
    WHEN risk_dimension = 'app_criticality_assessment' THEN 'app_criticality_assessment'
    -- Handle any NULLs or unexpected values
    ELSE risk_dimension
END
WHERE risk_dimension IS NOT NULL;

-- Step 3: Drop old unique constraint and indexes
ALTER TABLE domain_risk
    DROP CONSTRAINT IF EXISTS uq_domain_risk_app_dimension;

DROP INDEX IF EXISTS idx_domain_risk_dimension;
DROP INDEX IF EXISTS idx_domain_risk_dimension_status;

-- Step 4: Create new unique constraint and indexes
ALTER TABLE domain_risk
    ADD CONSTRAINT uq_domain_risk_app_rating_dimension
    UNIQUE (app_id, risk_rating_dimension);

CREATE INDEX IF NOT EXISTS idx_domain_risk_rating_dimension
    ON domain_risk(risk_rating_dimension);

CREATE INDEX IF NOT EXISTS idx_domain_risk_rating_dimension_status
    ON domain_risk(risk_rating_dimension, status);

-- Step 5: Drop old column
ALTER TABLE domain_risk
    DROP COLUMN IF EXISTS risk_dimension;

-- Step 6: Add comment
COMMENT ON COLUMN domain_risk.risk_rating_dimension IS
    'Full risk rating dimension from derived_from (e.g., security_rating, availability_rating, app_criticality_assessment)';

-- ============================================
-- PART 3: Verification
-- ============================================

DO $$
DECLARE
    risk_item_count INT;
    domain_risk_count INT;
    rec RECORD;
BEGIN
    -- Count updated records
    SELECT COUNT(*) INTO risk_item_count
        FROM risk_item
        WHERE risk_rating_dimension IS NOT NULL;

    SELECT COUNT(*) INTO domain_risk_count
        FROM domain_risk
        WHERE risk_rating_dimension IS NOT NULL;

    RAISE NOTICE '================================================';
    RAISE NOTICE 'Column Rename Complete';
    RAISE NOTICE '================================================';
    RAISE NOTICE '  Risk Items Updated: %', risk_item_count;
    RAISE NOTICE '  Domain Risks Updated: %', domain_risk_count;
    RAISE NOTICE '================================================';

    -- Show distribution of risk_rating_dimension values
    RAISE NOTICE '';
    RAISE NOTICE 'Risk Rating Dimension Distribution (Risk Items):';
    RAISE NOTICE '---------------------------------------------------';
    FOR rec IN (
        SELECT
            COALESCE(risk_rating_dimension, '(null)') as dimension,
            COUNT(*) as count
        FROM risk_item
        GROUP BY risk_rating_dimension
        ORDER BY COUNT(*) DESC
    ) LOOP
        RAISE NOTICE '  % : % items', rec.dimension, rec.count;
    END LOOP;

    RAISE NOTICE '';
    RAISE NOTICE 'Risk Rating Dimension Distribution (Domain Risks):';
    RAISE NOTICE '---------------------------------------------------';
    FOR rec IN (
        SELECT
            COALESCE(risk_rating_dimension, '(null)') as dimension,
            COUNT(*) as count
        FROM domain_risk
        GROUP BY risk_rating_dimension
        ORDER BY COUNT(*) DESC
    ) LOOP
        RAISE NOTICE '  % : % domain risks', rec.dimension, rec.count;
    END LOOP;
END $$;
