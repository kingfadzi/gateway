-- Migration V5: Backfill domain_risk table from existing risk_item records
-- Groups risk items by app_id + domain and creates corresponding domain_risk aggregates

-- Step 1: Create domain_risk records for each unique (app_id, derived_from) combination
-- Use CTE to pre-compute domain from derived_from
WITH risk_item_with_domain AS (
    SELECT
        ri.risk_item_id,
        ri.app_id,
        ri.status,
        ri.priority,
        ri.priority_score,
        ri.opened_at,
        COALESCE(pf.derived_from, 'app_criticality_assessment') as derived_from,
        COALESCE(
            regexp_replace(pf.derived_from, '_rating$', ''),
            'governance'
        ) as domain,
        app.name as app_name
    FROM risk_item ri
    LEFT JOIN profile_field pf ON ri.profile_field_id = pf.id
    LEFT JOIN application app ON ri.app_id = app.app_id
    WHERE ri.domain_risk_id IS NULL
)
INSERT INTO domain_risk (
    domain_risk_id,
    app_id,
    domain,
    derived_from,
    arb,
    title,
    description,
    total_items,
    open_items,
    high_priority_items,
    overall_priority,
    priority_score,
    status,
    assigned_arb,
    opened_at,
    last_item_added_at,
    created_at,
    updated_at
)
SELECT
    concat('dr_', replace(gen_random_uuid()::text, '-', '')) as domain_risk_id,
    app_id,
    domain,
    derived_from,
    -- ARB routing: derived_from -> arb name (e.g., 'security_rating' -> 'security_arb')
    regexp_replace(derived_from, '_rating$', '_arb') as arb,
    -- Title: domain + app name
    concat(
        initcap(domain),
        ' risks for ',
        COALESCE(app_name, app_id)
    ) as title,
    concat(
        'Aggregated ',
        lower(domain),
        ' domain risks for application'
    ) as description,
    COUNT(*) as total_items,
    COUNT(*) FILTER (WHERE status IN ('OPEN', 'IN_PROGRESS')) as open_items,
    COUNT(*) FILTER (WHERE priority IN ('CRITICAL', 'HIGH')) as high_priority_items,
    -- Overall priority: highest priority among items
    CASE
        WHEN COUNT(*) FILTER (WHERE priority = 'CRITICAL') > 0 THEN 'CRITICAL'
        WHEN COUNT(*) FILTER (WHERE priority = 'HIGH') > 0 THEN 'HIGH'
        WHEN COUNT(*) FILTER (WHERE priority = 'MEDIUM') > 0 THEN 'MEDIUM'
        ELSE 'LOW'
    END as overall_priority,
    -- Priority score: max item score + bonuses
    LEAST(100,
        COALESCE(MAX(priority_score), 0) +
        LEAST(10, COUNT(*) FILTER (WHERE priority IN ('CRITICAL', 'HIGH')) * 2) +
        LEAST(5, GREATEST(0, COUNT(*) FILTER (WHERE status IN ('OPEN', 'IN_PROGRESS')) - 3))
    ) as priority_score,
    -- Status: RESOLVED if all items closed, otherwise PENDING_ARB_REVIEW
    CASE
        WHEN COUNT(*) FILTER (WHERE status IN ('OPEN', 'IN_PROGRESS')) = 0 THEN 'RESOLVED'
        ELSE 'PENDING_ARB_REVIEW'
    END as status,
    -- Assigned ARB same as routing ARB initially
    regexp_replace(derived_from, '_rating$', '_arb') as assigned_arb,
    MIN(opened_at) as opened_at,
    MAX(opened_at) as last_item_added_at,
    now() as created_at,
    now() as updated_at
FROM risk_item_with_domain
GROUP BY app_id, domain, derived_from, app_name
ON CONFLICT (app_id, domain) DO NOTHING;  -- Skip if domain_risk already exists

-- Step 2: Update risk_item records to link them to their domain_risk
-- Use CTE again to compute domain for each risk_item
WITH risk_item_with_domain AS (
    SELECT
        ri.risk_item_id,
        ri.app_id,
        COALESCE(
            regexp_replace(pf.derived_from, '_rating$', ''),
            'governance'
        ) as domain
    FROM risk_item ri
    LEFT JOIN profile_field pf ON ri.profile_field_id = pf.id
    WHERE ri.domain_risk_id IS NULL
)
UPDATE risk_item
SET domain_risk_id = dr.domain_risk_id,
    updated_at = now()
FROM risk_item_with_domain riwd
JOIN domain_risk dr ON riwd.app_id = dr.app_id AND riwd.domain = dr.domain
WHERE risk_item.risk_item_id = riwd.risk_item_id;

-- Step 3: Verify migration results (logged in console)
DO $$
DECLARE
    domain_risk_count INTEGER;
    risk_item_count INTEGER;
    unlinked_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO domain_risk_count FROM domain_risk;
    SELECT COUNT(*) INTO risk_item_count FROM risk_item;
    SELECT COUNT(*) INTO unlinked_count FROM risk_item WHERE domain_risk_id IS NULL;

    RAISE NOTICE 'Migration V5 completed:';
    RAISE NOTICE '  Domain risks created/existing: %', domain_risk_count;
    RAISE NOTICE '  Total risk items: %', risk_item_count;
    RAISE NOTICE '  Unlinked risk items: %', unlinked_count;

    IF unlinked_count > 0 THEN
        RAISE WARNING '  % risk items could not be linked to domain risks', unlinked_count;
    END IF;
END $$;
