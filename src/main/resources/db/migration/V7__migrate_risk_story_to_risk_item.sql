-- ============================================================================
-- V7: Migrate risk_story data to risk_item + domain_risk
-- ============================================================================
-- Purpose: Migrate all existing risk_story records to the new risk_item architecture
-- Preserves rich content: hypothesis, condition, consequence, control_refs
-- ============================================================================

-- Step 1: Create domain risks for risk_story data (one per app + domain)
-- ============================================================================
INSERT INTO domain_risk (
    domain_risk_id, app_id, domain, derived_from, arb, assigned_arb,
    title, description,
    status, opened_at, assigned_at,
    total_items, open_items, high_priority_items, priority_score,
    overall_priority, overall_severity,
    created_at, updated_at
)
SELECT DISTINCT ON (rs.app_id, COALESCE(pf.derived_from, 'app_criticality_assessment'))
    gen_random_uuid()::text as domain_risk_id,
    rs.app_id,
    -- Calculate domain from derived_from
    COALESCE(regexp_replace(pf.derived_from, '_rating$', ''), 'governance') as domain,
    COALESCE(pf.derived_from, 'app_criticality_assessment') as derived_from,
    -- Route ARB based on derived_from
    CASE
        WHEN pf.derived_from = 'security_rating' THEN 'security'
        WHEN pf.derived_from = 'integrity_rating' THEN 'integrity'
        WHEN pf.derived_from = 'availability_rating' THEN 'availability'
        WHEN pf.derived_from = 'resilience_rating' THEN 'resilience'
        WHEN pf.derived_from = 'confidentiality_rating' THEN 'confidentiality'
        WHEN pf.derived_from = 'app_criticality_assessment' THEN 'governance'
        ELSE 'governance'
    END as arb,
    CASE
        WHEN pf.derived_from = 'security_rating' THEN 'security'
        WHEN pf.derived_from = 'integrity_rating' THEN 'integrity'
        WHEN pf.derived_from = 'availability_rating' THEN 'availability'
        WHEN pf.derived_from = 'resilience_rating' THEN 'resilience'
        WHEN pf.derived_from = 'confidentiality_rating' THEN 'confidentiality'
        WHEN pf.derived_from = 'app_criticality_assessment' THEN 'governance'
        ELSE 'governance'
    END as assigned_arb,
    -- Generate title
    CONCAT(
        UPPER(SUBSTRING(COALESCE(regexp_replace(pf.derived_from, '_rating$', ''), 'governance'), 1, 1)),
        SUBSTRING(COALESCE(regexp_replace(pf.derived_from, '_rating$', ''), 'governance'), 2),
        ' Domain Risks'
    ) as title,
    CONCAT(
        'Aggregated ',
        COALESCE(regexp_replace(pf.derived_from, '_rating$', ''), 'governance'),
        ' risks derived from ',
        COALESCE(pf.derived_from, 'app_criticality_assessment'),
        ' assessment. Review and remediate individual risk items to improve overall compliance posture.'
    ) as description,
    'PENDING_ARB_REVIEW' as status,
    MIN(rs.opened_at) as opened_at,
    MIN(rs.opened_at) as assigned_at,
    -- Initialize counters (will be recalculated in step 3)
    0 as total_items,
    0 as open_items,
    0 as high_priority_items,
    0 as priority_score,
    'LOW'::text as overall_priority,
    'Low' as overall_severity,
    MIN(rs.created_at) as created_at,
    MAX(rs.updated_at) as updated_at
FROM risk_story rs
LEFT JOIN profile_field pf ON rs.profile_field_id = pf.id
GROUP BY rs.app_id, pf.derived_from
ON CONFLICT (app_id, domain) DO NOTHING;

-- Step 2: Migrate risk stories to risk items
-- ============================================================================
INSERT INTO risk_item (
    risk_item_id, domain_risk_id,
    app_id, field_key, profile_field_id, triggering_evidence_id, track_id,
    title, description,
    hypothesis, condition, consequence, control_refs,  -- ✅ Rich content preserved
    priority, severity, priority_score, evidence_status,
    status, resolution, resolution_comment,
    creation_type, raised_by,
    opened_at, resolved_at,
    policy_requirement_snapshot,
    created_at, updated_at
)
SELECT
    CONCAT('migrated_', rs.risk_id) as risk_item_id,
    -- Link to domain risk (match app_id + derived_from → domain)
    (SELECT dr.domain_risk_id
     FROM domain_risk dr
     LEFT JOIN profile_field pf2 ON rs.profile_field_id = pf2.id
     WHERE dr.app_id = rs.app_id
     AND dr.domain = COALESCE(regexp_replace(pf2.derived_from, '_rating$', ''), 'governance')
     LIMIT 1) as domain_risk_id,
    rs.app_id,
    rs.field_key,
    rs.profile_field_id,
    rs.triggering_evidence_id,
    rs.track_id,
    rs.title,
    -- Preserve description from attributes if present, otherwise build from hypothesis/condition/consequence
    COALESCE(
        NULLIF(TRIM(rs.attributes->>'description'), ''),
        CONCAT(
            COALESCE('Evidence may indicate risk in ' || rs.field_key || ' implementation ', ''),
            COALESCE('IF: ' || rs.hypothesis || ' ', ''),
            COALESCE('THEN: ' || rs.consequence, '')
        )
    ) as description,
    -- ✅ Preserve rich content structure
    rs.hypothesis,
    rs.condition,
    rs.consequence,
    rs.control_refs,
    -- Map severity string to priority enum
    CASE
        WHEN LOWER(rs.severity) IN ('critical', 'high') THEN 'HIGH'
        WHEN LOWER(rs.severity) = 'medium' THEN 'MEDIUM'
        WHEN LOWER(rs.severity) = 'low' THEN 'LOW'
        ELSE 'MEDIUM'
    END as priority,
    rs.severity,
    -- Calculate priority score based on severity
    CASE
        WHEN LOWER(rs.severity) IN ('critical', 'high') THEN 80
        WHEN LOWER(rs.severity) = 'medium' THEN 50
        WHEN LOWER(rs.severity) = 'low' THEN 20
        ELSE 50
    END as priority_score,
    -- Default to missing, will be recalculated by evidence status calculation
    'missing' as evidence_status,
    -- Map old status enum to new status enum
    CASE
        WHEN rs.status IN ('PENDING_SME_REVIEW', 'AWAITING_EVIDENCE', 'UNDER_REVIEW')
            THEN 'OPEN'
        WHEN rs.status = 'APPROVED'
            THEN 'RESOLVED'
        WHEN rs.status IN ('REJECTED', 'WAIVED')
            THEN 'WAIVED'
        WHEN rs.status = 'CLOSED'
            THEN 'CLOSED'
        ELSE 'OPEN'
    END as status,
    -- Map resolution
    CASE
        WHEN rs.status = 'APPROVED' THEN 'REMEDIATED'
        WHEN rs.status IN ('REJECTED', 'WAIVED') THEN 'RISK_ACCEPTED'
        WHEN rs.status = 'CLOSED' THEN 'VERIFIED'
        ELSE NULL
    END as resolution,
    -- Use closure_reason as resolution comment
    rs.closure_reason as resolution_comment,
    -- Map creation type
    CASE
        WHEN rs.creation_type = 'MANUAL_SME_INITIATED' THEN 'MANUAL_SME_INITIATED'
        WHEN rs.creation_type = 'SYSTEM_AUTO_CREATION' THEN 'SYSTEM_AUTO_CREATION'
        ELSE 'MANUAL_SME_INITIATED'
    END as creation_type,
    rs.raised_by,
    rs.opened_at,
    CASE WHEN rs.status = 'APPROVED' THEN rs.reviewed_at ELSE NULL END as resolved_at,
    rs.policy_requirement_snapshot,
    rs.created_at,
    rs.updated_at
FROM risk_story rs
WHERE NOT EXISTS (
    -- Avoid duplicates if migration is run multiple times
    SELECT 1 FROM risk_item ri
    WHERE ri.risk_item_id = CONCAT('migrated_', rs.risk_id)
);

-- Step 3: Recalculate domain risk aggregations
-- ============================================================================
UPDATE domain_risk dr
SET
    total_items = (
        SELECT COUNT(*)
        FROM risk_item ri
        WHERE ri.domain_risk_id = dr.domain_risk_id
    ),
    open_items = (
        SELECT COUNT(*)
        FROM risk_item ri
        WHERE ri.domain_risk_id = dr.domain_risk_id
        AND ri.status IN ('OPEN', 'IN_PROGRESS')
    ),
    high_priority_items = (
        SELECT COUNT(*)
        FROM risk_item ri
        WHERE ri.domain_risk_id = dr.domain_risk_id
        AND ri.priority IN ('CRITICAL', 'HIGH')
    ),
    priority_score = LEAST(100, (
        SELECT COALESCE(MAX(ri.priority_score), 0) +
               -- High priority bonus: min(10, count * 2)
               LEAST(10, (SELECT COUNT(*) FROM risk_item ri2
                         WHERE ri2.domain_risk_id = dr.domain_risk_id
                         AND ri2.priority IN ('CRITICAL', 'HIGH')) * 2) +
               -- Volume bonus: min(5, (open_count - 3))
               GREATEST(0, LEAST(5, (SELECT COUNT(*) FROM risk_item ri3
                                    WHERE ri3.domain_risk_id = dr.domain_risk_id
                                    AND ri3.status IN ('OPEN', 'IN_PROGRESS')) - 3))
        FROM risk_item ri
        WHERE ri.domain_risk_id = dr.domain_risk_id
        AND ri.status IN ('OPEN', 'IN_PROGRESS')
    )),
    overall_priority = (
        CASE
            WHEN (SELECT COALESCE(MAX(ri.priority_score), 0) FROM risk_item ri
                  WHERE ri.domain_risk_id = dr.domain_risk_id) >= 90 THEN 'CRITICAL'
            WHEN (SELECT COALESCE(MAX(ri.priority_score), 0) FROM risk_item ri
                  WHERE ri.domain_risk_id = dr.domain_risk_id) >= 70 THEN 'HIGH'
            WHEN (SELECT COALESCE(MAX(ri.priority_score), 0) FROM risk_item ri
                  WHERE ri.domain_risk_id = dr.domain_risk_id) >= 40 THEN 'MEDIUM'
            ELSE 'LOW'
        END
    ),
    overall_severity = (
        CASE
            WHEN (SELECT COALESCE(MAX(ri.priority_score), 0) FROM risk_item ri
                  WHERE ri.domain_risk_id = dr.domain_risk_id) >= 90 THEN 'Critical'
            WHEN (SELECT COALESCE(MAX(ri.priority_score), 0) FROM risk_item ri
                  WHERE ri.domain_risk_id = dr.domain_risk_id) >= 70 THEN 'High'
            WHEN (SELECT COALESCE(MAX(ri.priority_score), 0) FROM risk_item ri
                  WHERE ri.domain_risk_id = dr.domain_risk_id) >= 40 THEN 'Medium'
            ELSE 'Low'
        END
    ),
    status = (
        CASE
            WHEN (SELECT COUNT(*) FROM risk_item ri
                  WHERE ri.domain_risk_id = dr.domain_risk_id
                  AND ri.status IN ('OPEN', 'IN_PROGRESS')) = 0
            THEN 'RESOLVED'
            ELSE 'PENDING_ARB_REVIEW'
        END
    ),
    updated_at = NOW()
WHERE EXISTS (SELECT 1 FROM risk_item ri WHERE ri.domain_risk_id = dr.domain_risk_id);

-- Step 4: Create mapping table for reference
-- ============================================================================
CREATE TABLE IF NOT EXISTS risk_story_to_item_mapping (
    old_risk_id TEXT PRIMARY KEY,
    new_risk_item_id TEXT NOT NULL,
    app_id TEXT NOT NULL,
    field_key TEXT NOT NULL,
    migrated_at TIMESTAMPTZ DEFAULT NOW()
);

INSERT INTO risk_story_to_item_mapping (old_risk_id, new_risk_item_id, app_id, field_key)
SELECT
    rs.risk_id,
    CONCAT('migrated_', rs.risk_id),
    rs.app_id,
    rs.field_key
FROM risk_story rs
ON CONFLICT (old_risk_id) DO NOTHING;

-- Step 5: Log migration results
-- ============================================================================
DO $$
DECLARE
    story_count INTEGER;
    item_count INTEGER;
    domain_count INTEGER;
    unmapped_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO story_count FROM risk_story;
    SELECT COUNT(*) INTO item_count FROM risk_item WHERE risk_item_id LIKE 'migrated_%';
    SELECT COUNT(*) INTO domain_count FROM domain_risk WHERE created_at >= NOW() - INTERVAL '1 minute';
    SELECT COUNT(*) INTO unmapped_count FROM risk_story rs
        LEFT JOIN risk_story_to_item_mapping m ON rs.risk_id = m.old_risk_id
        WHERE m.old_risk_id IS NULL;

    RAISE NOTICE '==============================================';
    RAISE NOTICE 'Risk Story → Risk Item Migration Complete';
    RAISE NOTICE '==============================================';
    RAISE NOTICE 'Risk stories in source: %', story_count;
    RAISE NOTICE 'Risk items created: %', item_count;
    RAISE NOTICE 'Domain risks created: %', domain_count;
    RAISE NOTICE 'Unmigrated stories: %', unmapped_count;
    RAISE NOTICE '==============================================';

    IF unmapped_count > 0 THEN
        RAISE WARNING 'Some risk stories were not migrated. Check risk_story_to_item_mapping table.';
    END IF;
END $$;
