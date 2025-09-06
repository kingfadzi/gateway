-- Migration: Add USER_ATTESTED status to evidence_field_link constraint
-- Date: 2025-09-06
-- Purpose: Support user attestation workflow with distinct USER_ATTESTED state

BEGIN;

-- Drop existing constraint
ALTER TABLE evidence_field_link DROP CONSTRAINT IF EXISTS chk_efl_status;

-- Add new constraint with USER_ATTESTED status
ALTER TABLE evidence_field_link 
ADD CONSTRAINT chk_efl_status 
CHECK (link_status IN ('ATTACHED', 'PENDING_PO_REVIEW', 'PENDING_SME_REVIEW', 'APPROVED', 'USER_ATTESTED', 'REJECTED'));

COMMIT;