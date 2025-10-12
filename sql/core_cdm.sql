-- =========================================
-- FRESH START: drop views, functions, tables
-- =========================================
DROP VIEW IF EXISTS v_profile_fields CASCADE;
DROP VIEW IF EXISTS v_app_profiles_latest CASCADE;

DROP FUNCTION IF EXISTS set_updated_at() CASCADE;
DROP FUNCTION IF EXISTS enforce_ev_claim_same_app() CASCADE;
DROP FUNCTION IF EXISTS assert_track_app_match() CASCADE;

-- Risk aggregation tables
DROP TABLE IF EXISTS risk_comment CASCADE;
DROP TABLE IF EXISTS risk_item CASCADE;
DROP TABLE IF EXISTS domain_risk CASCADE;
DROP TABLE IF EXISTS evidence_field_link CASCADE;

-- Existing tables
DROP TABLE IF EXISTS evidence CASCADE;
DROP TABLE IF EXISTS control_claim CASCADE;
DROP TABLE IF EXISTS document_related_evidence_field CASCADE;
DROP TABLE IF EXISTS document_version CASCADE;
DROP TABLE IF EXISTS document CASCADE;
DROP TABLE IF EXISTS profile_field CASCADE;
DROP TABLE IF EXISTS profile CASCADE;
DROP TABLE IF EXISTS track CASCADE;
DROP TABLE IF EXISTS service_instances CASCADE;
DROP TABLE IF EXISTS application CASCADE;

-- Drop enums (converted to TEXT for Hibernate compatibility)
DROP TYPE IF EXISTS evidence_field_link_status CASCADE;

-- Needed for gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- =========================
-- NOTE: Using TEXT with CHECK constraints instead of ENUMs 
-- for better Hibernate compatibility
-- =========================

-- =========================
-- APPLICATION  (TEXT IDs)
-- =========================
CREATE TABLE application (
                             app_id                       text PRIMARY KEY,
                             scope                        text NOT NULL DEFAULT 'application',
                             parent_app_id                text,
                             parent_app_name              text,
                             name                         text,
                             business_service_name        text,
                             app_criticality_assessment   text,
                             security_rating              text,
                             confidentiality_rating       text,
                             integrity_rating             text,
                             availability_rating          text,
                             resilience_rating            text,
                             business_application_sys_id  text,
                             architecture_hosting         text,
                             jira_backlog_id              text,
                             lean_control_service_id      text,
                             repo_id                      text,
                             operational_status           text,
                             transaction_cycle            text,
                             transaction_cycle_id         text,
                             application_type             text,
                             application_tier             text,
                             architecture_type            text,
                             install_type                 text,
                             house_position               text,
                             product_owner                text,
                             product_owner_brid           text,
                             system_architect             text,
                             system_architect_brid        text,
                             onboarding_status            text NOT NULL DEFAULT 'pending',
                             owner_id                     text,
                             created_at                   timestamptz NOT NULL DEFAULT now(),
                             updated_at                   timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_application_scope  ON application(scope);
CREATE INDEX IF NOT EXISTS idx_application_parent ON application(parent_app_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_application_name ON application(lower(name));

-- =========================
-- TRACK (bounded engagement; 1:1 external unit)
-- =========================
CREATE TABLE track (
                       track_id       text PRIMARY KEY,
                       app_id         text NOT NULL REFERENCES application(app_id) ON DELETE CASCADE,
                       title          text,
                       intent         text,            -- 'compliance'|'risk'|'security'|'architecture'
                       status         text,            -- 'open'|'in_review'|'closed'
                       result         text,            -- 'pass'|'fail'|'waived'|'abandoned'

    -- Primary external anchor (denormalized)
                       provider       text,            -- 'jira'|'snow'|'gitlab'|'manual'|'policy'|...
                       resource_type  text,            -- 'epic'|'change'|'mr'|'note'|'control'|...
                       resource_id    text,            -- native key, e.g. 'JIT-123','CHG0034567','!45'
                       uri            text,            -- canonical link (jira://..., snow://..., etc.)
                       attributes     jsonb NOT NULL DEFAULT '{}'::jsonb,  -- raw payload/fields

                       opened_at      timestamptz,
                       closed_at      timestamptz,
                       created_at     timestamptz NOT NULL DEFAULT now(),
                       updated_at     timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_track_app       ON track(app_id);
CREATE INDEX IF NOT EXISTS idx_track_status    ON track(status);
CREATE INDEX IF NOT EXISTS idx_track_provider  ON track(provider);
CREATE INDEX IF NOT EXISTS idx_track_resource  ON track(resource_type, resource_id);
CREATE INDEX IF NOT EXISTS idx_track_attrs_gin ON track USING GIN (attributes);


-- =========================
-- SERVICE_INSTANCES (TEXT IDs)
-- =========================
CREATE TABLE service_instances (
                                   it_service_instance_sysid  text PRIMARY KEY,
                                   app_id                     text NOT NULL REFERENCES application(app_id) ON DELETE CASCADE,
                                   environment                text,
                                   it_business_service_sysid  text,
                                   business_application_sysid text,
                                   service_offering_join      text,
                                   service_instance           text,
                                   install_type               text,
                                   service_classification     text,
                                   created_at                 timestamptz NOT NULL DEFAULT now(),
                                   updated_at                 timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_si_app_id       ON service_instances(app_id);
CREATE INDEX IF NOT EXISTS idx_si_environment  ON service_instances(environment);
CREATE INDEX IF NOT EXISTS idx_si_ba_sysid     ON service_instances(business_application_sysid);
CREATE INDEX IF NOT EXISTS idx_si_svc_off_join ON service_instances(service_offering_join);

-- =========================
-- PROFILE  (app-anchored snapshots)
-- =========================
CREATE TABLE profile (
                         profile_id   text PRIMARY KEY,
                         app_id       text NOT NULL REFERENCES application(app_id) ON DELETE CASCADE,
                         version      integer NOT NULL,
                         snapshot_at  timestamptz NOT NULL DEFAULT now(),
                         created_at   timestamptz NOT NULL DEFAULT now(),
                         updated_at   timestamptz NOT NULL DEFAULT now(),
                         CONSTRAINT uq_profile UNIQUE (app_id, version)
);

CREATE INDEX IF NOT EXISTS idx_profile_app ON profile(app_id);
CREATE INDEX IF NOT EXISTS idx_profile_ver ON profile(version);

-- =========================
-- PROFILE_FIELD
-- =========================
CREATE TABLE profile_field (
                               id            text PRIMARY KEY,
                               profile_id    text NOT NULL REFERENCES profile(profile_id) ON DELETE CASCADE,
                               field_key     text NOT NULL,
                               derived_from  text,          -- present in ERD
                               value         jsonb,
                               confidence    text,
                               source_system text,
                               source_ref    text,
                               collected_at  timestamptz,
                               created_at    timestamptz NOT NULL DEFAULT now(),
                               updated_at    timestamptz NOT NULL DEFAULT now(),
                               CONSTRAINT uq_profile_field UNIQUE (profile_id, field_key)
);

CREATE INDEX IF NOT EXISTS idx_profile_field_profile ON profile_field(profile_id);
CREATE INDEX IF NOT EXISTS idx_profile_field_key     ON profile_field(field_key);

-- =========================
-- DOCUMENT (+ versions + tags)
-- =========================
CREATE TABLE document (
                          document_id   text PRIMARY KEY,
                          app_id        text NOT NULL REFERENCES application(app_id) ON DELETE CASCADE,
                          title         text,
                          canonical_url text,
                          source_type   text,
                          owners        text,
                          link_health   int,
                          created_at    timestamptz NOT NULL DEFAULT now(),
                          updated_at    timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_document_app ON document(app_id);

CREATE TABLE document_version (
                                  doc_version_id  text PRIMARY KEY,
                                  document_id     text NOT NULL REFERENCES document(document_id) ON DELETE CASCADE,
                                  version_id      text,
                                  url_at_version  text,
                                  author          text,
                                  source_date     timestamptz,  -- Date from source system (commit date, page last modified, etc.)
                                  created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_docver_doc ON document_version(document_id);

CREATE TABLE document_related_evidence_field (
                              document_id  text NOT NULL REFERENCES document(document_id) ON DELETE CASCADE,
                              field_key    text NOT NULL,
                              PRIMARY KEY (document_id, field_key)
);

-- =========================
-- CONTROL_CLAIM  (app-anchored; explicit scope; optional track)
-- =========================
CREATE TABLE control_claim (
                               claim_id       text PRIMARY KEY,
                               app_id         text NOT NULL REFERENCES application(app_id) ON DELETE CASCADE,

                               field_key      text NOT NULL,    -- aligns with PROFILE_FIELD.field_key
                               method         text,
                               status         text,

    -- explicit scope under the app (required)
                               scope_type     text NOT NULL,    -- 'application'|'jira_issue'|'environment'|'service_instance'
                               scope_id       text NOT NULL,

    -- optional explicit track binding (engagement)
                               track_id       text REFERENCES track(track_id) ON DELETE SET NULL,

                               submitted_at   timestamptz,
                               reviewed_at    timestamptz,
                               assigned_at    timestamptz,
                               comment        text,
                               decision_json  jsonb,
                               created_at     timestamptz NOT NULL DEFAULT now(),
                               updated_at     timestamptz NOT NULL DEFAULT now(),

                               CONSTRAINT ck_claim_scope_type
                                   CHECK (scope_type IN ('application','jira_issue','environment','service_instance'))
);

CREATE INDEX IF NOT EXISTS idx_claim_app        ON control_claim(app_id);
CREATE INDEX IF NOT EXISTS idx_claim_app_field  ON control_claim(app_id, field_key);
CREATE INDEX IF NOT EXISTS idx_claim_scope      ON control_claim(scope_type, scope_id);
CREATE INDEX IF NOT EXISTS idx_claim_track      ON control_claim(track_id);

-- =========================
-- EVIDENCE  (app-anchored; bridges to claim/profile_field; optional track)
-- =========================
CREATE TABLE evidence (
                          evidence_id       text PRIMARY KEY DEFAULT concat('ev_', replace(gen_random_uuid()::text, '-', '')),
                          app_id            text NOT NULL REFERENCES application(app_id) ON DELETE CASCADE,

                          profile_field_id  text REFERENCES profile_field(id) ON DELETE CASCADE,
                          claim_id          text REFERENCES control_claim(claim_id) ON DELETE SET NULL,

                          uri               text NOT NULL,
                          type              text,
                          sha256            text,
                          source_system     text,
                          submitted_by      text,

                          valid_from        timestamptz DEFAULT now(),
                          valid_until       timestamptz,

                          status            text NOT NULL DEFAULT 'active',  -- 'active'|'superseded'|'revoked'
                          revoked_at        timestamptz,
                          reviewed_by       text,
                          reviewed_at       timestamptz,

                          related_evidence_fields text,

    -- optional explicit track binding (engagement)
                          track_id          text REFERENCES track(track_id) ON DELETE SET NULL,

                          added_at          timestamptz NOT NULL DEFAULT now(),
                          created_at        timestamptz NOT NULL DEFAULT now(),
                          updated_at        timestamptz NOT NULL DEFAULT now(),

    -- doc links (present in ERD)
                          document_id       text REFERENCES document(document_id) ON DELETE SET NULL,
                          doc_version_id    text REFERENCES document_version(doc_version_id) ON DELETE SET NULL,

                          -- Fixed: Removed UNIQUE constraint that was causing duplicate key errors
                          -- CONSTRAINT uq_evidence_pf_uri UNIQUE (profile_field_id, uri),
                          CONSTRAINT chk_evidence_status CHECK (status IN ('active','superseded','revoked'))
);

CREATE INDEX IF NOT EXISTS idx_evidence_app           ON evidence(app_id);
CREATE INDEX IF NOT EXISTS idx_evidence_pf            ON evidence(profile_field_id);
CREATE INDEX IF NOT EXISTS idx_evidence_claim         ON evidence(claim_id);
CREATE INDEX IF NOT EXISTS idx_evidence_status        ON evidence(status);
CREATE INDEX IF NOT EXISTS idx_evidence_valid_window  ON evidence(valid_from, valid_until);
CREATE INDEX IF NOT EXISTS idx_evidence_sha256        ON evidence(sha256);
CREATE INDEX IF NOT EXISTS idx_evidence_track         ON evidence(track_id);
CREATE INDEX IF NOT EXISTS idx_evidence_doc           ON evidence(document_id, doc_version_id);

-- Optional SHA256 uniqueness constraint per profile field (commented out to avoid issues)
-- DO $$
--     BEGIN
--         IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'ux_evidence_pf_sha') THEN
--             CREATE UNIQUE INDEX ux_evidence_pf_sha ON evidence(profile_field_id, sha256);
--         END IF;
--     END $$;

-- =========================
-- EVIDENCE_FIELD_LINK (Junction with Workflow)
-- =========================
CREATE TABLE evidence_field_link (
    evidence_id         TEXT NOT NULL,
    profile_field_id    TEXT NOT NULL,
    app_id             TEXT NOT NULL,
    -- Use TEXT instead of ENUM for better Hibernate compatibility
    link_status        TEXT NOT NULL DEFAULT 'PENDING_SME_REVIEW',
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
    CONSTRAINT fk_efl_app FOREIGN KEY (app_id) REFERENCES application(app_id) ON DELETE CASCADE,
    -- Constraint to ensure valid status values
    CONSTRAINT chk_efl_status CHECK (link_status IN ('ATTACHED', 'PENDING_PO_REVIEW', 'PENDING_SME_REVIEW', 'APPROVED', 'USER_ATTESTED', 'REJECTED'))
);

CREATE INDEX IF NOT EXISTS idx_efl_evidence ON evidence_field_link(evidence_id);
CREATE INDEX IF NOT EXISTS idx_efl_field ON evidence_field_link(profile_field_id);
CREATE INDEX IF NOT EXISTS idx_efl_app ON evidence_field_link(app_id);
CREATE INDEX IF NOT EXISTS idx_efl_status ON evidence_field_link(link_status);

-- =========================
-- DOMAIN_RISK (Aggregated risks per domain per application)
-- =========================
CREATE TABLE domain_risk (
    domain_risk_id    TEXT PRIMARY KEY,
    app_id            TEXT NOT NULL,
    domain            TEXT NOT NULL,           -- security, integrity, availability, etc.
    derived_from      TEXT NOT NULL,           -- security_rating, integrity_rating, etc.
    arb               TEXT NOT NULL,           -- security_arb, integrity_arb, etc.

    -- Aggregated metadata
    title             TEXT,
    description       TEXT,
    total_items       INTEGER DEFAULT 0,
    open_items        INTEGER DEFAULT 0,
    high_priority_items INTEGER DEFAULT 0,

    -- Priority & severity (calculated from items)
    overall_priority  TEXT,                    -- CRITICAL, HIGH, MEDIUM, LOW
    overall_severity  TEXT,                    -- high, medium, low
    priority_score    INTEGER,                 -- Calculated: 0-100

    -- Status
    status            TEXT NOT NULL DEFAULT 'PENDING_ARB_REVIEW',

    -- Assignment
    assigned_arb      TEXT,
    assigned_at       TIMESTAMPTZ,

    -- Lifecycle
    opened_at         TIMESTAMPTZ NOT NULL,
    closed_at         TIMESTAMPTZ,
    last_item_added_at TIMESTAMPTZ,

    -- Audit
    created_at        TIMESTAMPTZ DEFAULT now(),
    updated_at        TIMESTAMPTZ DEFAULT now(),

    -- Foreign Keys
    CONSTRAINT fk_domain_risk_app FOREIGN KEY (app_id) REFERENCES application(app_id) ON DELETE CASCADE,

    -- Unique constraint: one domain risk per domain per app
    CONSTRAINT uk_app_domain UNIQUE (app_id, domain),

    -- CHECK constraints
    CONSTRAINT chk_domain_risk_status CHECK (status IN (
        'PENDING_ARB_REVIEW', 'UNDER_ARB_REVIEW', 'AWAITING_REMEDIATION',
        'IN_PROGRESS', 'RESOLVED', 'CLOSED'
    ))
);

-- Domain Risk Indexes
CREATE INDEX IF NOT EXISTS idx_domain_risk_app ON domain_risk(app_id);
CREATE INDEX IF NOT EXISTS idx_domain_risk_arb ON domain_risk(assigned_arb);
CREATE INDEX IF NOT EXISTS idx_domain_risk_status ON domain_risk(status);
CREATE INDEX IF NOT EXISTS idx_domain_risk_priority ON domain_risk(priority_score DESC);
CREATE INDEX IF NOT EXISTS idx_domain_risk_domain ON domain_risk(domain);

-- =========================
-- RISK_ITEM (Individual evidence-level risks)
-- =========================
CREATE TABLE risk_item (
    risk_item_id           TEXT PRIMARY KEY,
    domain_risk_id         TEXT NOT NULL,

    -- References
    app_id                 TEXT NOT NULL,
    field_key              TEXT NOT NULL,
    profile_field_id       TEXT,
    triggering_evidence_id TEXT,
    track_id               TEXT,

    -- Content
    title                  TEXT,
    description            TEXT,

    -- Priority & severity
    priority               TEXT,                   -- CRITICAL, HIGH, MEDIUM, LOW (from registry)
    severity               TEXT,                   -- high, medium, low (from evidence status)
    priority_score         INTEGER,                -- Calculated score 0-100
    evidence_status        TEXT,                   -- missing, expiring, expired, rejected

    -- Status
    status                 TEXT NOT NULL DEFAULT 'OPEN',
    resolution             TEXT,
    resolution_comment     TEXT,

    -- Lifecycle
    creation_type          TEXT,
    raised_by              TEXT,
    opened_at              TIMESTAMPTZ NOT NULL,
    resolved_at            TIMESTAMPTZ,

    -- Snapshot
    policy_requirement_snapshot JSONB,

    -- Audit
    created_at             TIMESTAMPTZ DEFAULT now(),
    updated_at             TIMESTAMPTZ DEFAULT now(),

    -- Foreign Keys
    CONSTRAINT fk_risk_item_domain FOREIGN KEY (domain_risk_id) REFERENCES domain_risk(domain_risk_id) ON DELETE CASCADE,
    CONSTRAINT fk_risk_item_app FOREIGN KEY (app_id) REFERENCES application(app_id) ON DELETE CASCADE,
    CONSTRAINT fk_risk_item_profile_field FOREIGN KEY (profile_field_id) REFERENCES profile_field(id) ON DELETE SET NULL,
    CONSTRAINT fk_risk_item_evidence FOREIGN KEY (triggering_evidence_id) REFERENCES evidence(evidence_id) ON DELETE SET NULL,
    CONSTRAINT fk_risk_item_track FOREIGN KEY (track_id) REFERENCES track(track_id) ON DELETE SET NULL,

    -- CHECK constraints
    CONSTRAINT chk_risk_item_status CHECK (status IN (
        'OPEN', 'IN_PROGRESS', 'RESOLVED', 'WAIVED', 'CLOSED'
    )),
    CONSTRAINT chk_risk_item_creation_type CHECK (creation_type IN (
        'AUTO', 'MANUAL_CREATION', 'MANUAL_SME_INITIATED', 'SYSTEM_AUTO_CREATION'
    ))
);

-- Risk Item Indexes
CREATE INDEX IF NOT EXISTS idx_risk_item_domain_risk ON risk_item(domain_risk_id);
CREATE INDEX IF NOT EXISTS idx_risk_item_app ON risk_item(app_id);
CREATE INDEX IF NOT EXISTS idx_risk_item_field ON risk_item(field_key);
CREATE INDEX IF NOT EXISTS idx_risk_item_evidence ON risk_item(triggering_evidence_id);
CREATE INDEX IF NOT EXISTS idx_risk_item_priority ON risk_item(priority_score DESC);
CREATE INDEX IF NOT EXISTS idx_risk_item_status ON risk_item(status);
CREATE INDEX IF NOT EXISTS idx_risk_item_profile_field ON risk_item(profile_field_id);

-- =========================
-- RISK_COMMENT (Comments and discussion thread for risk items)
-- =========================
CREATE TABLE risk_comment (
    comment_id      TEXT PRIMARY KEY,
    risk_item_id    TEXT NOT NULL,
    comment_type    TEXT NOT NULL,             -- GENERAL, STATUS_CHANGE, REVIEW, RESOLUTION
    comment_text    TEXT NOT NULL,
    commented_by    TEXT NOT NULL,
    commented_at    TIMESTAMPTZ NOT NULL,

    -- Metadata
    is_internal     BOOLEAN DEFAULT FALSE,     -- Internal ARB notes vs visible to PO
    created_at      TIMESTAMPTZ DEFAULT now(),
    updated_at      TIMESTAMPTZ DEFAULT now(),

    -- Foreign Keys
    CONSTRAINT fk_risk_comment_risk_item FOREIGN KEY (risk_item_id) REFERENCES risk_item(risk_item_id) ON DELETE CASCADE,

    -- CHECK constraints
    CONSTRAINT chk_risk_comment_type CHECK (comment_type IN (
        'GENERAL', 'STATUS_CHANGE', 'REVIEW', 'RESOLUTION'
    ))
);

-- Risk Comment Indexes
CREATE INDEX IF NOT EXISTS idx_risk_comment_risk_item_id ON risk_comment(risk_item_id);
CREATE INDEX IF NOT EXISTS idx_risk_comment_commented_at ON risk_comment(commented_at DESC);
CREATE INDEX IF NOT EXISTS idx_risk_comment_commented_by ON risk_comment(commented_by);
CREATE INDEX IF NOT EXISTS idx_risk_comment_type ON risk_comment(comment_type);

-- Table comments for documentation
COMMENT ON TABLE domain_risk IS 'Aggregated domain-level risks (ARB/SME view) - one per domain per app';
COMMENT ON TABLE risk_item IS 'Individual evidence-level risk items (PO workbench view) - linked to profile fields';
COMMENT ON TABLE risk_comment IS 'Comments and discussion thread for risk items - supports ARB/PO collaboration';

COMMENT ON COLUMN domain_risk.arb IS 'ARB routing based on derived_from field (security_arb, integrity_arb, etc.)';
COMMENT ON COLUMN domain_risk.assigned_arb IS 'Currently assigned ARB (can differ from arb for workload balancing)';
COMMENT ON COLUMN domain_risk.priority_score IS 'Aggregate priority score (0-100) calculated from risk items';

COMMENT ON COLUMN risk_item.creation_type IS 'AUTO (from evidence), MANUAL_CREATION (ARB initiated), MANUAL_SME_INITIATED, SYSTEM_AUTO_CREATION';
COMMENT ON COLUMN risk_item.priority_score IS 'Calculated: priority_base (from registry) * evidence_status_multiplier';
COMMENT ON COLUMN risk_item.policy_requirement_snapshot IS 'Snapshot of policy requirements at risk creation time';

COMMENT ON COLUMN risk_comment.comment_type IS 'GENERAL, STATUS_CHANGE, REVIEW, RESOLUTION';
COMMENT ON COLUMN risk_comment.is_internal IS 'TRUE for internal ARB notes, FALSE for PO-visible comments';

-- ===========================
-- Updated Evidence Field Link Status
-- ===========================
-- Migrate existing PENDING_REVIEW data to PENDING_SME_REVIEW and update constraint

-- Update existing data first
UPDATE evidence_field_link 
SET link_status = 'PENDING_SME_REVIEW' 
WHERE link_status = 'PENDING_REVIEW';

-- Drop old constraint
ALTER TABLE evidence_field_link DROP CONSTRAINT IF EXISTS chk_efl_status;

-- Add new constraint with updated status values including USER_ATTESTED
ALTER TABLE evidence_field_link 
ADD CONSTRAINT chk_efl_status 
CHECK (link_status IN ('ATTACHED', 'PENDING_PO_REVIEW', 'PENDING_SME_REVIEW', 'APPROVED', 'USER_ATTESTED', 'REJECTED'));


CREATE INDEX IF NOT EXISTS idx_application_criticality ON application(app_criticality_assessment);
CREATE INDEX IF NOT EXISTS idx_application_app_type ON application(application_type);
CREATE INDEX IF NOT EXISTS idx_application_arch_type ON application(architecture_type);
CREATE INDEX IF NOT EXISTS idx_application_install_type ON application(install_type);
CREATE INDEX IF NOT EXISTS idx_profile_field_key ON profile_field(field_key);
CREATE INDEX IF NOT EXISTS idx_efl_reviewed_by ON evidence_field_link(reviewed_by);
CREATE INDEX IF NOT EXISTS idx_evidence_submitted_by ON evidence(submitted_by);

-- GIN index for text search
CREATE INDEX IF NOT EXISTS idx_gin_app_name ON application USING gin (name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_gin_pf_field_key ON profile_field USING gin (field_key gin_trgm_ops);

-- =========================
-- Triggers & Guards
-- =========================
CREATE OR REPLACE FUNCTION set_updated_at()
    RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END$$;

-- Enforce: rows referencing a TRACK must share the same app_id as that TRACK
CREATE OR REPLACE FUNCTION assert_track_app_match()
    RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE tr_app text;
BEGIN
    IF NEW.track_id IS NULL THEN
        RETURN NEW;
    END IF;

    SELECT app_id INTO tr_app FROM track WHERE track_id = NEW.track_id;
    IF tr_app IS NULL THEN
        RAISE EXCEPTION 'track_id % not found', NEW.track_id;
    ELSIF NEW.app_id <> tr_app THEN
        RAISE EXCEPTION 'Row app_id % must equal Track.app_id % (track_id=%)',
            NEW.app_id, tr_app, NEW.track_id;
    END IF;

    RETURN NEW;
END$$;

-- Enforce: evidence tied to a claim must share the same app
CREATE OR REPLACE FUNCTION enforce_ev_claim_same_app()
    RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE c_app text;
BEGIN
    IF NEW.claim_id IS NULL THEN
        RETURN NEW;
    END IF;

    SELECT app_id INTO c_app FROM control_claim WHERE claim_id = NEW.claim_id;
    IF c_app IS NULL THEN
        RAISE EXCEPTION 'Claim % not found', NEW.claim_id;
    ELSIF NEW.app_id <> c_app THEN
        RAISE EXCEPTION 'Evidence.app_id (%) must equal Claim.app_id (%) for claim %',
            NEW.app_id, c_app, NEW.claim_id;
    END IF;

    RETURN NEW;
END$$;

DO $$
    BEGIN
        -- updated_at refreshers
        IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_application_updated_at') THEN
            CREATE TRIGGER trg_application_updated_at BEFORE UPDATE ON application
                FOR EACH ROW EXECUTE FUNCTION set_updated_at();
        END IF;

        IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_track_updated_at') THEN
            CREATE TRIGGER trg_track_updated_at BEFORE UPDATE ON track
                FOR EACH ROW EXECUTE FUNCTION set_updated_at();
        END IF;

        IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_profile_updated_at') THEN
            CREATE TRIGGER trg_profile_updated_at BEFORE UPDATE ON profile
                FOR EACH ROW EXECUTE FUNCTION set_updated_at();
        END IF;

        IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_profile_field_updated_at') THEN
            CREATE TRIGGER trg_profile_field_updated_at BEFORE UPDATE ON profile_field
                FOR EACH ROW EXECUTE FUNCTION set_updated_at();
        END IF;

        IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_document_updated_at') THEN
            CREATE TRIGGER trg_document_updated_at BEFORE UPDATE ON document
                FOR EACH ROW EXECUTE FUNCTION set_updated_at();
        END IF;

        IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_document_version_updated_at') THEN
            CREATE TRIGGER trg_document_version_updated_at BEFORE UPDATE ON document_version
                FOR EACH ROW EXECUTE FUNCTION set_updated_at();
        END IF;

        IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_control_claim_updated_at') THEN
            CREATE TRIGGER trg_control_claim_updated_at BEFORE UPDATE ON control_claim
                FOR EACH ROW EXECUTE FUNCTION set_updated_at();
        END IF;

        IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_evidence_updated_at') THEN
            CREATE TRIGGER trg_evidence_updated_at BEFORE UPDATE ON evidence
                FOR EACH ROW EXECUTE FUNCTION set_updated_at();
        END IF;

        IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_service_instances_updated_at') THEN
            CREATE TRIGGER trg_service_instances_updated_at BEFORE UPDATE ON service_instances
                FOR EACH ROW EXECUTE FUNCTION set_updated_at();
        END IF;

        -- Risk Aggregation table triggers
        IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_evidence_field_link_updated_at') THEN
            CREATE TRIGGER trg_evidence_field_link_updated_at BEFORE UPDATE ON evidence_field_link
                FOR EACH ROW EXECUTE FUNCTION set_updated_at();
        END IF;

        IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_domain_risk_updated_at') THEN
            CREATE TRIGGER trg_domain_risk_updated_at BEFORE UPDATE ON domain_risk
                FOR EACH ROW EXECUTE FUNCTION set_updated_at();
        END IF;

        IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_risk_item_updated_at') THEN
            CREATE TRIGGER trg_risk_item_updated_at BEFORE UPDATE ON risk_item
                FOR EACH ROW EXECUTE FUNCTION set_updated_at();
        END IF;

        IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_risk_comment_updated_at') THEN
            CREATE TRIGGER trg_risk_comment_updated_at BEFORE UPDATE ON risk_comment
                FOR EACH ROW EXECUTE FUNCTION set_updated_at();
        END IF;

        -- Guards: app consistency when binding to a TRACK
        IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_cc_track_app_guard') THEN
            CREATE TRIGGER trg_cc_track_app_guard
                BEFORE INSERT OR UPDATE ON control_claim
                FOR EACH ROW EXECUTE FUNCTION assert_track_app_match();
        END IF;

        IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_ev_track_app_guard') THEN
            CREATE TRIGGER trg_ev_track_app_guard
                BEFORE INSERT OR UPDATE ON evidence
                FOR EACH ROW EXECUTE FUNCTION assert_track_app_match();
        END IF;

        -- Evidence tied to a claim must match claim.app_id
        IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_ev_claim_same_app') THEN
            CREATE TRIGGER trg_ev_claim_same_app
                BEFORE INSERT OR UPDATE ON evidence
                FOR EACH ROW EXECUTE FUNCTION enforce_ev_claim_same_app();
        END IF;
    END$$;

-- =========================
-- Helpful views (app-anchored)
-- =========================
CREATE OR REPLACE VIEW v_app_profiles_latest AS
SELECT p.*
FROM profile p
         JOIN (
    SELECT app_id, MAX(version) AS max_version
    FROM profile
    GROUP BY app_id
) lv
              ON p.app_id = lv.app_id
                  AND p.version = lv.max_version;

CREATE OR REPLACE VIEW v_profile_fields AS
SELECT
    pf.profile_id,
    p.app_id,
    p.version,
    pf.field_key,
    pf.derived_from,
    pf.value,
    pf.source_system,
    pf.source_ref,
    pf.collected_at
FROM profile p
         JOIN profile_field pf ON pf.profile_id = p.profile_id;