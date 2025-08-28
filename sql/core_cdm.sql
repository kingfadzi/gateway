-- =========================================
-- FRESH START: drop views, functions, tables
-- =========================================
DROP VIEW IF EXISTS v_profile_fields CASCADE;
DROP VIEW IF EXISTS v_app_profiles_latest CASCADE;

DROP FUNCTION IF EXISTS set_updated_at() CASCADE;
DROP FUNCTION IF EXISTS enforce_ev_claim_same_app() CASCADE;
DROP FUNCTION IF EXISTS assert_track_app_match() CASCADE;

DROP TABLE IF EXISTS evidence CASCADE;
DROP TABLE IF EXISTS control_claim CASCADE;
DROP TABLE IF EXISTS document_related_evidence_field CASCADE;
DROP TABLE IF EXISTS document_version CASCADE;
DROP TABLE IF EXISTS document CASCADE;
DROP TABLE IF EXISTS profile_field CASCADE;
DROP TABLE IF EXISTS profile CASCADE;
DROP TABLE IF EXISTS track_external_ref CASCADE;   -- NEW
DROP TABLE IF EXISTS external_ref CASCADE;         -- NEW
DROP TABLE IF EXISTS track CASCADE;                -- ensure track dropped here
DROP TABLE IF EXISTS service_instances CASCADE;
DROP TABLE IF EXISTS application CASCADE;

-- Needed for gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS pgcrypto;

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
                          evidence_id       text PRIMARY KEY DEFAULT concat('ev_', md5(gen_random_uuid()::text)),
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

                          CONSTRAINT uq_evidence_pf_uri UNIQUE (profile_field_id, uri),
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

DO $$
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'ux_evidence_pf_sha') THEN
            CREATE UNIQUE INDEX ux_evidence_pf_sha ON evidence(profile_field_id, sha256);
        END IF;
    END $$;

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
