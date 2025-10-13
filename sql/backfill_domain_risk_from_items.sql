-- =========================================
-- Backfill domain_risk table from risk_item
-- =========================================
-- This script creates domain_risk records by aggregating existing risk_items
-- Run this after migrating from risk_story to risk_item

-- Step 1: Create domain_risk records for each unique app_id + domain combination
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
    overall_severity,
    priority_score,
    status,
    assigned_arb,
    opened_at,
    last_item_added_at,
    created_at,
    updated_at
)
SELECT
    -- Generate domain_risk_id
    concat('dr_', replace(gen_random_uuid()::text, '-', '')) as domain_risk_id,

    -- Core identification
    ri.app_id,
    CASE
        WHEN pf.derived_from = 'security_rating' THEN 'security'
        WHEN pf.derived_from = 'integrity_rating' THEN 'integrity'
        WHEN pf.derived_from = 'availability_rating' THEN 'availability'
        WHEN pf.derived_from = 'resilience_rating' THEN 'resilience'
        WHEN pf.derived_from = 'confidentiality_rating' THEN 'confidentiality'
        WHEN pf.derived_from = 'app_criticality_assessment' THEN 'governance'
        ELSE COALESCE(REPLACE(pf.derived_from, '_rating', ''), 'unknown')
    END as domain,
    COALESCE(pf.derived_from, 'unknown') as derived_from,

    -- ARB routing
    CASE
        WHEN pf.derived_from = 'security_rating' THEN 'security_arb'
        WHEN pf.derived_from = 'integrity_rating' THEN 'integrity_arb'
        WHEN pf.derived_from = 'availability_rating' THEN 'availability_arb'
        WHEN pf.derived_from = 'resilience_rating' THEN 'resilience_arb'
        WHEN pf.derived_from = 'confidentiality_rating' THEN 'confidentiality_arb'
        WHEN pf.derived_from = 'app_criticality_assessment' THEN 'governance_arb'
        ELSE 'governance_arb'
    END as arb,

    -- Title and description
    concat(
        CASE
            WHEN pf.derived_from = 'security_rating' THEN 'Security'
            WHEN pf.derived_from = 'integrity_rating' THEN 'Integrity'
            WHEN pf.derived_from = 'availability_rating' THEN 'Availability'
            WHEN pf.derived_from = 'resilience_rating' THEN 'Resilience'
            WHEN pf.derived_from = 'confidentiality_rating' THEN 'Confidentiality'
            WHEN pf.derived_from = 'app_criticality_assessment' THEN 'Governance'
            ELSE 'Unknown'
        END,
        ' risks for ',
        a.name
    ) as title,
    concat('Aggregated ',
        CASE
            WHEN pf.derived_from = 'security_rating' THEN 'security'
            WHEN pf.derived_from = 'integrity_rating' THEN 'integrity'
            WHEN pf.derived_from = 'availability_rating' THEN 'availability'
            WHEN pf.derived_from = 'resilience_rating' THEN 'resilience'
            WHEN pf.derived_from = 'confidentiality_rating' THEN 'confidentiality'
            WHEN pf.derived_from = 'app_criticality_assessment' THEN 'governance'
            ELSE 'unknown'
        END,
        ' domain risks'
    ) as description,

    -- Aggregated counts
    COUNT(*) as total_items,
    COUNT(*) FILTER (WHERE ri.status IN ('OPEN', 'IN_PROGRESS')) as open_items,
    COUNT(*) FILTER (WHERE ri.priority IN ('CRITICAL', 'HIGH') AND ri.status IN ('OPEN', 'IN_PROGRESS')) as high_priority_items,

    -- Overall priority (max priority from items)
    (ARRAY_AGG(ri.priority ORDER BY
        CASE ri.priority
            WHEN 'CRITICAL' THEN 4
            WHEN 'HIGH' THEN 3
            WHEN 'MEDIUM' THEN 2
            WHEN 'LOW' THEN 1
            ELSE 0
        END DESC
    ))[1] as overall_priority,

    -- Overall severity (derived from max priority)
    CASE
        WHEN MAX(CASE ri.priority
            WHEN 'CRITICAL' THEN 4
            WHEN 'HIGH' THEN 3
            WHEN 'MEDIUM' THEN 2
            WHEN 'LOW' THEN 1
            ELSE 0
        END) >= 3 THEN 'high'
        WHEN MAX(CASE ri.priority
            WHEN 'CRITICAL' THEN 4
            WHEN 'HIGH' THEN 3
            WHEN 'MEDIUM' THEN 2
            WHEN 'LOW' THEN 1
            ELSE 0
        END) = 2 THEN 'medium'
        ELSE 'low'
    END as overall_severity,

    -- Priority score (max item score + bonuses)
    LEAST(100,
        COALESCE(MAX(ri.priority_score), 0) +
        LEAST(10, COUNT(*) FILTER (WHERE ri.priority IN ('CRITICAL', 'HIGH') AND ri.status IN ('OPEN', 'IN_PROGRESS')) * 2) +
        LEAST(5, GREATEST(0, COUNT(*) FILTER (WHERE ri.status IN ('OPEN', 'IN_PROGRESS')) - 3))
    ) as priority_score,

    -- Status (based on open items)
    CASE
        WHEN COUNT(*) FILTER (WHERE ri.status IN ('OPEN', 'IN_PROGRESS')) = 0 THEN 'RESOLVED'
        WHEN COUNT(*) FILTER (WHERE ri.status = 'IN_PROGRESS') > 0 THEN 'IN_PROGRESS'
        ELSE 'PENDING_ARB_REVIEW'
    END as status,

    -- Assigned ARB (same as arb for initial backfill)
    CASE
        WHEN pf.derived_from = 'security_rating' THEN 'security_arb'
        WHEN pf.derived_from = 'integrity_rating' THEN 'integrity_arb'
        WHEN pf.derived_from = 'availability_rating' THEN 'availability_arb'
        WHEN pf.derived_from = 'resilience_rating' THEN 'resilience_arb'
        WHEN pf.derived_from = 'confidentiality_rating' THEN 'confidentiality_arb'
        WHEN pf.derived_from = 'app_criticality_assessment' THEN 'governance_arb'
        ELSE 'governance_arb'
    END as assigned_arb,

    -- Lifecycle timestamps
    MIN(ri.opened_at) as opened_at,
    MAX(ri.opened_at) as last_item_added_at,
    MIN(ri.created_at) as created_at,
    MAX(ri.updated_at) as updated_at

FROM risk_item ri
-- Join to get derived_from for domain/ARB mapping
LEFT JOIN profile_field pf ON ri.profile_field_id = pf.id
-- Join to get app name for title
LEFT JOIN application a ON ri.app_id = a.app_id
-- Only backfill items that don't already have a domain_risk_id
WHERE ri.domain_risk_id IS NULL
  OR ri.domain_risk_id = ''
GROUP BY
    ri.app_id,
    pf.derived_from,
    a.name
-- Only create domain_risk if it doesn't already exist
ON CONFLICT (app_id, domain) DO NOTHING;

-- Step 2: Update risk_items to link to their domain_risk
UPDATE risk_item ri
SET domain_risk_id = dr.domain_risk_id
FROM domain_risk dr
LEFT JOIN profile_field pf ON ri.profile_field_id = pf.id
WHERE ri.app_id = dr.app_id
  AND dr.domain = CASE
        WHEN pf.derived_from = 'security_rating' THEN 'security'
        WHEN pf.derived_from = 'integrity_rating' THEN 'integrity'
        WHEN pf.derived_from = 'availability_rating' THEN 'availability'
        WHEN pf.derived_from = 'resilience_rating' THEN 'resilience'
        WHEN pf.derived_from = 'confidentiality_rating' THEN 'confidentiality'
        WHEN pf.derived_from = 'app_criticality_assessment' THEN 'governance'
        ELSE COALESCE(REPLACE(pf.derived_from, '_rating', ''), 'unknown')
    END
  AND (ri.domain_risk_id IS NULL OR ri.domain_risk_id = '');

-- Step 3: Display results
SELECT
    dr.domain,
    dr.arb,
    COUNT(*) as num_apps,
    SUM(dr.total_items) as total_items,
    SUM(dr.open_items) as total_open,
    SUM(dr.high_priority_items) as total_high_priority
FROM domain_risk dr
GROUP BY dr.domain, dr.arb
ORDER BY dr.domain;
