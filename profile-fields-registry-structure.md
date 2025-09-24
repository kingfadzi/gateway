# Profile Fields Registry Structure

## Overview
This registry defines compliance and governance requirements based on security ratings across different domains.

## Rating Systems
- **Confidentiality Rating**: A (Restricted) → D (Public)
- **Security Rating**: A1 (Highest) → A2 (High) → B (Medium) → C (Low) → D (Minimal)
- **Integrity Rating**: A (Highest) → D (Lowest)
- **Availability Rating**: A (Highest) → D (Lowest)
- **Resilience Rating**: 0 (Highest) → 4 (Lowest)
- **App Criticality Assessment**: A (Critical) → D (Low)

## Field Mapping by Rating Source

| Rating Source | Field Key | A/A1/0 | B/A2/1 | C/2 | D/3 | 4 | Notes |
|---------------|-----------|---------|---------|-----|-----|---|-------|
| **confidentiality_rating** | confidentiality_level | restricted (90d, true) | confidential (90d, true) | internal (180d, false) | public (365d, false) | - | A-D scale |
| | data_residency_control | required_in_region (90d, true) | preferred_in_region (90d, true) | permitted_cross_region (180d, false) | unrestricted (365d, false) | - | A-D scale |
| | de_identification | mandatory_strong (90d, true) | required (90d, true) | recommended (180d, false) | optional (365d, false) | - | A-D scale |
| | access_review | quarterly (90d, true) | semi_annual (180d, true) | annual (365d, false) | n/a (0d, false) | - | A-D scale |
| | tpsp_attestation | required (365d, true) | required (365d, true) | recommended (365d, false) | n/a (0d, false) | - | A-D scale |
| | data_retention_policy | defined+enforced (365d, true) | defined (365d, true) | recommended (365d, false) | n/a (0d, false) | - | A-D scale |
| | data_deletion_evidence | required (365d, true) | required (365d, true) | optional (365d, false) | n/a (0d, false) | - | A-D scale |
| **security_rating** | encryption_at_rest | required (90d, true) | required (90d, true) | required (180d, true) | optional (365d, false) | optional (365d, false) | A1-A2-B-C-D scale |
| | encryption_in_transit | required (90d, true) | required (90d, true) | required (180d, true) | recommended (365d, false) | recommended (365d, false) | A1-A2-B-C-D scale |
| | security_testing | external_pentest+scans (365d, true) | internal_pentest+scans (365d, true) | sast+dast_quarterly (90d, true) | sast_on_release (180d, false) | sast_optional (365d, false) | A1-A2-B-C-D scale |
| | secrets_management | centralized_required (90d, true) | centralized_required (90d, true) | centralized_recommended (180d, true) | centralized_recommended (365d, false) | minimal_ok (365d, false) | A1-A2-B-C-D scale |
| | key_rotation_max | 90d (90d, true) | 180d (180d, true) | 365d (365d, true) | 365d (365d, false) | n/a (0d, false) | A1-A2-B-C-D scale |
| | mfa_enforcement | required (90d, true) | required (90d, true) | recommended (180d, true) | optional (365d, false) | n/a (0d, false) | A1-A2-B-C-D scale |
| | privileged_access_mgmt | centralized_required (90d, true) | centralized_required (90d, true) | centralized_recommended (180d, true) | manual_ok (365d, false) | n/a (0d, false) | A1-A2-B-C-D scale |
| | patching_sla | critical_30d (90d, true) | critical_30d (90d, true) | critical_60d (180d, true) | best_effort (365d, false) | n/a (0d, false) | A1-A2-B-C-D scale |
| | dependency_management | sbom_required (90d, true) | sbom_required (180d, true) | sbom_recommended (365d, true) | optional (365d, false) | n/a (0d, false) | A1-A2-B-C-D scale |
| | network_segmentation | pci_cde_required (365d, true) | segmentation_required (365d, true) | segmentation_required (365d, true) | recommended (365d, false) | n/a (0d, false) | A1-A2-B-C-D scale |
| | waf_protection | required (90d, true) | required (90d, true) | required (90d, true) | recommended (180d, false) | n/a (0d, false) | A1-A2-B-C-D scale |
| | siem_integration | required (365d, true) | required (90d, true) | required (365d, true) | recommended (365d, false) | n/a (0d, false) | A1-A2-B-C-D scale |
| **integrity_rating** | data_validation | dual_control (90d, true) | strong_validation (90d, true) | standard_validation (180d, false) | minimal_validation (365d, false) | - |
| | reconciliation_frequency | daily (90d, true) | weekly (30d, true) | per_release (180d, false) | ad_hoc (180d, false) | - |
| | audit_logging | full+daily_review (90d, true) | full+periodic_review (90d, true) | enabled+sampled_review (180d, false) | basic_logging (365d, false) | - |
| | change_control | independent_review+regression (90d, true) | peer_review+regression (90d, true) | peer_review+unit_tests (90d, false) | optional_peer_review (180d, false) | - |
| | immutability_required | yes (90d, true) | yes (90d, true) | no (180d, false) | no (365d, false) | - |
| | log_retention | ≥1y (365d, true) | ≥6m (365d, true) | ≥90d (365d, false) | n/a (0d, false) | - |
| **availability_rating** | rto_hours | ≤1 hour (90d, true) | ≤4 hours (90d, true) | ≤24 hours (180d, false) | best_effort (365d, false) | - |
| | rpo_minutes | ≤5 minutes (90d, true) | ≤60 minutes (90d, true) | ≤240 minutes (180d, false) | best_effort (365d, false) | - |
| | ha_topology | active_active (90d, true) | active_passive (90d, true) | backup_restore (180d, false) | none (365d, false) | - |
| | monitoring_slos | ≥99.9%+alerting (90d, true) | ≥99.5%+alerting (90d, true) | ≥99.0% (180d, false) | none (365d, false) | - |
| | oncall_coverage | 24×7 (90d, true) | 16×5 (90d, true) | business_hours (90d, false) | none (365d, false) | - |
| **resilience_rating** | dr_test_frequency | semi_annual_live (180d, true) | annual_live (365d, true) | annual_tabletop (180d, false) | ad_hoc_tabletop (365d, false) | none (0d, false) |
| | backup_policy | encrypted_geo+tested (90d, true) | encrypted+tested (90d, true) | standard+periodic_test (180d, false) | standard_backups (365d, false) | optional (0d, false) |
| | failover_automation | automatic (90d, true) | semi_automatic (90d, true) | manual (180d, false) | manual (365d, false) | best_effort (0d, false) |
| | runbook_maturity | certified (90d, true) | approved (90d, true) | draft (30d, false) | draft (30d, false) | none (0d, false) |
| | chaos_testing | required (90d, true) | recommended (180d, true) | optional (365d, false) | none (0d, false) | none (0d, false) |
| | ir_plan | tested_quarterly (90d, true) | tested_annually (365d, true) | documented (365d, false) | n/a (0d, false) | n/a (0d, false) |
| | ir_exercise | annual_live (365d, true) | tabletop (365d, true) | optional (365d, false) | n/a (0d, false) | n/a (0d, false) |
| **app_criticality_assessment** | product_vision | mandatory (90d, true) | mandatory (90d, true) | recommended (180d, false) | optional (365d, false) | - |
| | product_roadmap | mandatory (90d, true) | mandatory (90d, true) | recommended (180d, false) | optional (365d, false) | - |
| | architecture_vision | mandatory (90d, true) | mandatory (90d, true) | recommended (180d, false) | optional (365d, false) | - |
| | service_vision | mandatory (90d, true) | mandatory (90d, true) | recommended (180d, false) | optional (365d, false) | - |
| | security_vision | mandatory (90d, true) | mandatory (90d, true) | recommended (180d, false) | optional (365d, false) | - |
| | test_vision | mandatory (90d, true) | mandatory (90d, true) | recommended (180d, false) | optional (365d, false) | - |

## Legend
- **(TTL, Review)**: Time-to-live and whether review is required
- **true**: Requires review
- **false**: No review required

## Compliance Frameworks Referenced
- **Internal**: Custom internal governance framework
- **SOC2**: Service Organization Control 2 (referenced in secrets_management)
- **PCI_DSS**: Payment Card Industry Data Security Standard (referenced in secrets_management)