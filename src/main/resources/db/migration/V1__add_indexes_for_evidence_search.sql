-- Add indexes to optimize the evidence workbench search query

-- Indexes for filtering
CREATE INDEX IF NOT EXISTS idx_application_criticality ON application(app_criticality_assessment);
CREATE INDEX IF NOT EXISTS idx_application_app_type ON application(application_type);
CREATE INDEX IF NOT EXISTS idx_application_arch_type ON application(architecture_type);
CREATE INDEX IF NOT EXISTS idx_application_install_type ON application(install_type);
CREATE INDEX IF NOT EXISTS idx_efl_reviewed_by ON evidence_field_link(reviewed_by);
CREATE INDEX IF NOT EXISTS idx_evidence_submitted_by ON evidence(submitted_by);

-- GIN indexes for text search (requires pg_trgm extension)
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX IF NOT EXISTS idx_gin_app_name ON application USING gin (name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_gin_pf_field_key ON profile_field USING gin (field_key gin_trgm_ops);
