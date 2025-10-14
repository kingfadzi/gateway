-- ============================================================================
-- V10: Add arb column to profile_field table
-- ============================================================================
-- Purpose: Store Architecture Review Board (ARB) assignment from registry
-- ARB values: data, security, operations, enterprise_architecture
-- ============================================================================

-- Add arb column to profile_field table
ALTER TABLE profile_field
    ADD COLUMN IF NOT EXISTS arb VARCHAR(100);

-- Create index for ARB filtering
CREATE INDEX IF NOT EXISTS idx_profile_field_arb
    ON profile_field(arb);

-- Comment
COMMENT ON COLUMN profile_field.arb IS 'Architecture Review Board assignment from registry';

-- Temporary mapping table (based on profile-fields.registry.yaml)
CREATE TEMP TABLE arb_mapping AS
SELECT field_key, arb FROM (VALUES
    -- Confidentiality fields → data ARB
    ('confidentiality_level', 'data'),
    ('data_residency_control', 'data'),
    ('de_identification', 'data'),
    ('access_review', 'data'),
    ('tpsp_attestation', 'data'),
    ('data_retention_policy', 'data'),
    ('data_deletion_evidence', 'data'),

    -- Security fields → security ARB
    ('encryption_at_rest', 'security'),
    ('encryption_in_transit', 'security'),
    ('security_testing', 'security'),
    ('secrets_management', 'security'),
    ('key_rotation_max', 'security'),
    ('mfa_enforcement', 'security'),
    ('privileged_access_mgmt', 'security'),
    ('patching_sla', 'security'),
    ('dependency_management', 'security'),
    ('network_segmentation', 'security'),
    ('waf_protection', 'security'),
    ('siem_integration', 'security'),

    -- Integrity fields → data ARB
    ('data_validation', 'data'),
    ('reconciliation_frequency', 'data'),
    ('audit_logging', 'data'),
    ('change_control', 'data'),
    ('immutability_required', 'data'),
    ('log_retention', 'data'),

    -- Availability fields → operations ARB
    ('rto_hours', 'operations'),
    ('rpo_minutes', 'operations'),
    ('ha_topology', 'operations'),
    ('monitoring_slos', 'operations'),
    ('oncall_coverage', 'operations'),

    -- Resilience fields → operations ARB
    ('dr_test_frequency', 'operations'),
    ('backup_policy', 'operations'),
    ('failover_automation', 'operations'),
    ('runbook_maturity', 'operations'),
    ('chaos_testing', 'operations'),
    ('ir_plan', 'operations'),
    ('ir_exercise', 'operations'),

    -- Governance fields → enterprise_architecture ARB
    ('product_vision', 'enterprise_architecture'),
    ('product_roadmap', 'enterprise_architecture'),
    ('architecture_vision', 'enterprise_architecture'),
    ('service_vision', 'enterprise_architecture'),
    ('security_vision', 'enterprise_architecture'),
    ('test_vision', 'enterprise_architecture')
) AS mapping(field_key, arb);

-- Backfill profile_field.arb from mapping
UPDATE profile_field pf
SET arb = m.arb
FROM arb_mapping m
WHERE pf.field_key = m.field_key;

-- Drop temp table
DROP TABLE arb_mapping;

-- Log results
DO $$
DECLARE
    total_records INT;
    updated_records INT;
    null_records INT;
BEGIN
    SELECT COUNT(*) INTO total_records FROM profile_field;
    SELECT COUNT(*) INTO updated_records FROM profile_field WHERE arb IS NOT NULL;
    SELECT COUNT(*) INTO null_records FROM profile_field WHERE arb IS NULL;

    RAISE NOTICE 'Profile Field ARB Backfill Complete:';
    RAISE NOTICE '  Total Records: %', total_records;
    RAISE NOTICE '  Updated (arb set): %', updated_records;
    RAISE NOTICE '  Remaining NULL: %', null_records;
END $$;
