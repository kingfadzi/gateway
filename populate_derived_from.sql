-- Migration script to populate derived_from column in profile_field table
-- This script maps field_key to derived_from based on the YAML registry

UPDATE profile_field 
SET derived_from = 'confidentiality_rating'
WHERE field_key IN ('confidentiality_level', 'data_residency_control', 'de_identification', 'access_review', 'tpsp_attestation', 'data_retention_policy', 'data_deletion_evidence');

UPDATE profile_field 
SET derived_from = 'security_rating' 
WHERE field_key IN ('encryption_at_rest', 'encryption_in_transit', 'security_testing', 'secrets_management', 'key_rotation_max', 'mfa_enforcement', 'privileged_access_mgmt', 'patching_sla', 'dependency_management', 'network_segmentation', 'waf_protection', 'siem_integration');

UPDATE profile_field 
SET derived_from = 'integrity_rating'
WHERE field_key IN ('data_validation', 'reconciliation_frequency', 'audit_logging', 'change_control', 'immutability_required');

UPDATE profile_field 
SET derived_from = 'availability_rating'
WHERE field_key IN ('rto_hours', 'rpo_minutes', 'ha_topology', 'monitoring_slos', 'oncall_coverage');

UPDATE profile_field 
SET derived_from = 'resilience_rating'
WHERE field_key IN ('dr_test_frequency', 'backup_policy', 'failover_automation', 'runbook_maturity', 'chaos_testing', 'ir_plan', 'ir_exercise');

UPDATE profile_field 
SET derived_from = 'artifact'
WHERE field_key IN ('product_vision', 'product_roadmap', 'architecture_vision', 'service_vision', 'security_vision', 'test_vision');

-- Verify the updates
SELECT 
    derived_from,
    COUNT(*) as count,
    array_agg(DISTINCT field_key ORDER BY field_key) as field_keys
FROM profile_field 
WHERE derived_from IS NOT NULL
GROUP BY derived_from
ORDER BY derived_from;