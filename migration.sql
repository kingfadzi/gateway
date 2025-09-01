-- Create missing enums and tables for risk management

-- Create evidence field link status enum if it doesn't exist
DO $$ 
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'evidence_field_link_status') THEN
        CREATE TYPE evidence_field_link_status AS ENUM ('ATTACHED', 'PENDING_REVIEW', 'APPROVED', 'REJECTED');
    END IF;
END $$;

-- Create risk status enum if it doesn't exist  
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'risk_status') THEN
        CREATE TYPE risk_status AS ENUM (
            'PENDING_SME_REVIEW', 
            'AWAITING_EVIDENCE', 
            'UNDER_REVIEW', 
            'APPROVED', 
            'REJECTED', 
            'WAIVED', 
            'CLOSED'
        );
    END IF;
END $$;

-- Create risk creation type enum if it doesn't exist
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'risk_creation_type') THEN
        CREATE TYPE risk_creation_type AS ENUM ('AUTO', 'MANUAL');
    END IF;
END $$;

-- Create evidence_field_link table if it doesn't exist
CREATE TABLE IF NOT EXISTS evidence_field_link (
    evidence_id         TEXT NOT NULL,
    profile_field_id    TEXT NOT NULL,
    app_id             TEXT NOT NULL,
    link_status        evidence_field_link_status NOT NULL DEFAULT 'ATTACHED',
    linked_by          TEXT NOT NULL,
    linked_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    reviewed_by        TEXT,
    reviewed_at        TIMESTAMPTZ,
    review_comment     TEXT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (evidence_id, profile_field_id),
    CONSTRAINT fk_efl_evidence FOREIGN KEY (evidence_id) REFERENCES evidence(evidence_id) ON DELETE CASCADE,
    CONSTRAINT fk_efl_profile_field FOREIGN KEY (profile_field_id) REFERENCES profile_field(id) ON DELETE CASCADE,
    CONSTRAINT fk_efl_app FOREIGN KEY (app_id) REFERENCES application(app_id) ON DELETE CASCADE
);

-- Create indexes if they don't exist
CREATE INDEX IF NOT EXISTS idx_efl_evidence ON evidence_field_link(evidence_id);
CREATE INDEX IF NOT EXISTS idx_efl_field ON evidence_field_link(profile_field_id);
CREATE INDEX IF NOT EXISTS idx_efl_app ON evidence_field_link(app_id);

-- Update risk_story table to add new columns if they don't exist
DO $$
BEGIN
    -- Add triggering_evidence_id column if it doesn't exist
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'risk_story' AND column_name = 'triggering_evidence_id') THEN
        ALTER TABLE risk_story ADD COLUMN triggering_evidence_id TEXT;
    END IF;

    -- Add creation_type column if it doesn't exist
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'risk_story' AND column_name = 'creation_type') THEN
        ALTER TABLE risk_story ADD COLUMN creation_type risk_creation_type DEFAULT 'MANUAL';
    END IF;

    -- Add assigned_sme column if it doesn't exist
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'risk_story' AND column_name = 'assigned_sme') THEN
        ALTER TABLE risk_story ADD COLUMN assigned_sme TEXT;
    END IF;

    -- Add assigned_at column if it doesn't exist
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'risk_story' AND column_name = 'assigned_at') THEN
        ALTER TABLE risk_story ADD COLUMN assigned_at TIMESTAMPTZ;
    END IF;

    -- Add review_comment column if it doesn't exist
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'risk_story' AND column_name = 'review_comment') THEN
        ALTER TABLE risk_story ADD COLUMN review_comment TEXT;
    END IF;
END $$;

-- Update risk_story status column type if it's currently text
DO $$
BEGIN
    -- Check if status column is text type and convert to enum
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'risk_story' AND column_name = 'status' AND data_type = 'text') THEN
        -- Convert existing text values to valid enum values, then change column type
        UPDATE risk_story SET status = 'PENDING_SME_REVIEW' WHERE status NOT IN ('PENDING_SME_REVIEW', 'AWAITING_EVIDENCE', 'UNDER_REVIEW', 'APPROVED', 'REJECTED', 'WAIVED', 'CLOSED');
        ALTER TABLE risk_story ALTER COLUMN status TYPE risk_status USING status::risk_status;
    END IF;
END $$;