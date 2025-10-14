-- ============================================================================
-- V13: Backfill profile_field_id in risk_item table
-- ============================================================================
-- Purpose: Populate missing profile_field_id values using app_id + field_key
-- Method: Join through profile table to find the correct profile_field
-- ============================================================================

-- Data quality check: Count records needing backfill
DO $$
DECLARE
    total_risk_items INT;
    missing_profile_field_id INT;
    percentage DECIMAL(5,2);
BEGIN
    SELECT COUNT(*) INTO total_risk_items FROM risk_item;
    SELECT COUNT(*) INTO missing_profile_field_id FROM risk_item WHERE profile_field_id IS NULL;

    IF total_risk_items > 0 THEN
        percentage := (missing_profile_field_id::DECIMAL / total_risk_items::DECIMAL) * 100;
    ELSE
        percentage := 0;
    END IF;

    RAISE NOTICE '=== Risk Item Profile Field ID Backfill - Pre-check ===';
    RAISE NOTICE 'Total risk items: %', total_risk_items;
    RAISE NOTICE 'Missing profile_field_id: % (%.2f%%)', missing_profile_field_id, percentage;
END $$;

-- Primary backfill strategy: Use app_id + field_key
-- This joins through the profile table to find the matching profile_field
UPDATE risk_item ri
SET profile_field_id = pf.id
FROM profile_field pf
JOIN profile p ON p.profile_id = pf.profile_id
WHERE p.app_id = ri.app_id
  AND pf.field_key = ri.field_key
  AND ri.profile_field_id IS NULL;

-- Fallback strategy: Use triggering_evidence_id → evidence_field_link
-- This handles edge cases where the primary method didn't find a match
UPDATE risk_item ri
SET profile_field_id = efl.profile_field_id
FROM evidence_field_link efl
WHERE efl.evidence_id = ri.triggering_evidence_id
  AND ri.profile_field_id IS NULL
  AND ri.triggering_evidence_id IS NOT NULL;

-- Data quality report: Show results
DO $$
DECLARE
    total_risk_items INT;
    filled_profile_field_id INT;
    still_missing INT;
    success_rate DECIMAL(5,2);
BEGIN
    SELECT COUNT(*) INTO total_risk_items FROM risk_item;
    SELECT COUNT(*) INTO filled_profile_field_id FROM risk_item WHERE profile_field_id IS NOT NULL;
    SELECT COUNT(*) INTO still_missing FROM risk_item WHERE profile_field_id IS NULL;

    IF total_risk_items > 0 THEN
        success_rate := (filled_profile_field_id::DECIMAL / total_risk_items::DECIMAL) * 100;
    ELSE
        success_rate := 0;
    END IF;

    RAISE NOTICE '';
    RAISE NOTICE '=== Risk Item Profile Field ID Backfill - Results ===';
    RAISE NOTICE 'Total risk items: %', total_risk_items;
    RAISE NOTICE 'Successfully filled: % (%.2f%%)', filled_profile_field_id, success_rate;
    RAISE NOTICE 'Still missing: %', still_missing;
    RAISE NOTICE '';

    -- Warning if still missing
    IF still_missing > 0 THEN
        RAISE WARNING 'There are % risk items still missing profile_field_id', still_missing;
        RAISE WARNING 'These may need manual investigation';
    ELSE
        RAISE NOTICE 'SUCCESS: All risk items now have profile_field_id!';
    END IF;
END $$;

-- Create diagnostic view for remaining issues (if any)
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

-- Show sample of any remaining issues
DO $$
DECLARE
    remaining_count INT;
BEGIN
    SELECT COUNT(*) INTO remaining_count FROM v_risk_item_missing_profile_field;

    IF remaining_count > 0 THEN
        RAISE NOTICE '';
        RAISE NOTICE '=== Sample of Remaining Issues (first 5) ===';
        RAISE NOTICE 'Use: SELECT * FROM v_risk_item_missing_profile_field;';
    END IF;
END $$;
