-- =========================================
-- FRESH START: drop views, function, tables
-- =========================================
DROP VIEW IF EXISTS v_profile_fields CASCADE;
DROP VIEW IF EXISTS v_app_profiles_latest CASCADE;

DROP FUNCTION IF EXISTS set_updated_at() CASCADE;
DROP FUNCTION IF EXISTS assert_release_app_match() CASCADE;
DROP FUNCTION IF EXISTS enforce_ev_claim_same_app() CASCADE;

DROP TABLE IF EXISTS evidence CASCADE;
DROP TABLE IF EXISTS profile_field CASCADE;
DROP TABLE IF EXISTS profile CASCADE;
DROP TABLE IF EXISTS control_claim CASCADE;
DROP TABLE IF EXISTS risk_story CASCADE;
DROP TABLE IF EXISTS release CASCADE;
DROP TABLE IF EXISTS application CASCADE;
DROP TABLE IF EXISTS service_instances CASCADE;

-- Needed for gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- =========================
-- APPLICATION  (TEXT IDs)
-- =========================
CREATE TABLE IF NOT EXISTS application (
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
-- SERVICE_INSTANCES (TEXT IDs)
-- =========================
CREATE TABLE IF NOT EXISTS service_instances (
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
-- RELEASE (app-scoped)
-- =========================
CREATE TABLE release (
                         release_id   text PRIMARY KEY,
                         app_id       text NOT NULL REFERENCES application(app_id) ON DELETE CASCADE,
                         version      text NOT NULL,                      -- e.g., REL-001 or semver
                         window_start timestamptz,
                         window_end   timestamptz,
                         created_at   timestamptz NOT NULL DEFAULT now(),
                         updated_at   timestamptz NOT NULL DEFAULT now(),
                         CONSTRAINT uq_release_per_app UNIQUE (app_id, version)
);

CREATE INDEX IF NOT EXISTS idx_release_app     ON release(app_id);
CREATE INDEX IF NOT EXISTS idx_release_window  ON release(window_start, window_end);

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

CREATE INDEX IF NOT EXISTS idx_profile_app    ON profile(app_id);
CREATE INDEX IF NOT EXISTS idx_profile_ver    ON profile(version);

-- =========================
-- PROFILE_FIELD
-- =========================
CREATE TABLE profile_field (
                               id            text PRIMARY KEY,
                               profile_id    text NOT NULL REFERENCES profile(profile_id) ON DELETE CASCADE,
                               field_key     text NOT NULL,
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
-- CONTROL_CLAIM  (app-anchored; explicit scope; optional release)
-- =========================
CREATE TABLE control_claim (
                               claim_id       text PRIMARY KEY,
                               app_id         text NOT NULL REFERENCES application(app_id) ON DELETE CASCADE,

                               field_key      text NOT NULL,    -- aligns with PROFILE_FIELD.field_key
                               method         text,
                               status         text,

    -- explicit scope under the app (required)
                               scope_type     text NOT NULL,    -- e.g., 'application'|'jira_issue'|'environment'|'service_instance'
                               scope_id       text NOT NULL,

    -- optional explicit release binding
                               release_id     text REFERENCES release(release_id) ON DELETE SET NULL,

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
CREATE INDEX IF NOT EXISTS idx_claim_release    ON control_claim(release_id);
CREATE INDEX IF NOT EXISTS idx_claim_scope      ON control_claim(scope_type, scope_id);

-- optional: prevent duplicate active claims per (app, field, scope)
-- CREATE UNIQUE INDEX IF NOT EXISTS ux_claim_app_field_scope
--   ON control_claim(app_id, field_key, scope_type, scope_id)
--   WHERE (status IS DISTINCT FROM 'archived');

-- =========================
-- EVIDENCE  (app-anchored; bridges to claim/profile_field; optional release)
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

                          status            text NOT NULL DEFAULT 'active',  -- active|superseded|revoked
                          revoked_at        timestamptz,
                          reviewed_by       text,
                          reviewed_at       timestamptz,

                          tags              text,

    -- optional explicit release binding
                          release_id        text REFERENCES release(release_id) ON DELETE SET NULL,

                          added_at          timestamptz NOT NULL DEFAULT now(),
                          created_at        timestamptz NOT NULL DEFAULT now(),
                          updated_at        timestamptz NOT NULL DEFAULT now(),

                          CONSTRAINT uq_evidence_pf_uri UNIQUE (profile_field_id, uri),
                          CONSTRAINT chk_evidence_status CHECK (status IN ('active','superseded','revoked'))
);

CREATE INDEX IF NOT EXISTS idx_evidence_app           ON evidence(app_id);
CREATE INDEX IF NOT EXISTS idx_evidence_pf            ON evidence(profile_field_id);
CREATE INDEX IF NOT EXISTS idx_evidence_claim         ON evidence(claim_id);
CREATE INDEX IF NOT EXISTS idx_evidence_release       ON evidence(release_id);
CREATE INDEX IF NOT EXISTS idx_evidence_status        ON evidence(status);
CREATE INDEX IF NOT EXISTS idx_evidence_valid_window  ON evidence(valid_from, valid_until);
CREATE INDEX IF NOT EXISTS idx_evidence_sha256        ON evidence(sha256);

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'ux_evidence_pf_sha') THEN
CREATE UNIQUE INDEX ux_evidence_pf_sha ON evidence(profile_field_id, sha256);
END IF;
END $$;

-- =========================
-- RISK_STORY  (app-anchored; explicit scope; optional release)
-- =========================
CREATE TABLE risk_story (
                            risk_key     text PRIMARY KEY,
                            app_id       text NOT NULL REFERENCES application(app_id) ON DELETE CASCADE,

                            domain       text NOT NULL,
                            status       text NOT NULL,

                            evidence_id  text REFERENCES evidence(evidence_id) ON DELETE SET NULL,

    -- explicit scope under the app
                            scope_type   text,
                            scope_id     text,

    -- optional explicit release binding
                            release_id   text REFERENCES release(release_id) ON DELETE SET NULL,

                            sla_due      timestamptz,
                            created_at   timestamptz NOT NULL DEFAULT now(),
                            updated_at   timestamptz NOT NULL DEFAULT now(),

                            CONSTRAINT ck_risk_scope_type
                                CHECK (scope_type IS NULL OR scope_type IN ('application','jira_issue','environment','service_instance'))
);

CREATE INDEX IF NOT EXISTS idx_risk_app       ON risk_story(app_id);
CREATE INDEX IF NOT EXISTS idx_risk_status    ON risk_story(status);
CREATE INDEX IF NOT EXISTS idx_risk_release   ON risk_story(release_id);
CREATE INDEX IF NOT EXISTS idx_risk_scope     ON risk_story(scope_type, scope_id);

-- =========================
-- Triggers: keep updated_at fresh
-- =========================
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  NEW.updated_at = now();
RETURN NEW;
END$$;

-- Enforce: if release_id is set, its app_id must equal the row's app_id
CREATE OR REPLACE FUNCTION assert_release_app_match()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE rel_app text;
BEGIN
  IF NEW.release_id IS NOT NULL THEN
SELECT r.app_id INTO rel_app FROM release r WHERE r.release_id = NEW.release_id;
IF rel_app IS NULL THEN
      RAISE EXCEPTION 'release_id % not found', NEW.release_id;
    ELSIF rel_app <> NEW.app_id THEN
      RAISE EXCEPTION 'release % belongs to app %, but row app_id is %', NEW.release_id, rel_app, NEW.app_id;
END IF;
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
END IF;

  IF NEW.app_id <> c_app THEN
    RAISE EXCEPTION 'Evidence.app_id (%) must equal Claim.app_id (%) for claim %',
      NEW.app_id, c_app, NEW.claim_id;
END IF;

RETURN NEW;
END$$;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_application_updated_at') THEN
CREATE TRIGGER trg_application_updated_at BEFORE UPDATE ON application
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

  IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_release_updated_at') THEN
CREATE TRIGGER trg_release_updated_at BEFORE UPDATE ON release
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_control_claim_updated_at') THEN
CREATE TRIGGER trg_control_claim_updated_at BEFORE UPDATE ON control_claim
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_risk_story_updated_at') THEN
CREATE TRIGGER trg_risk_story_updated_at BEFORE UPDATE ON risk_story
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

  -- App consistency checks for release bindings
  IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_cc_release_app_guard') THEN
CREATE TRIGGER trg_cc_release_app_guard
    BEFORE INSERT OR UPDATE ON control_claim
                         FOR EACH ROW EXECUTE FUNCTION assert_release_app_match();
END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_ev_release_app_guard') THEN
CREATE TRIGGER trg_ev_release_app_guard
    BEFORE INSERT OR UPDATE ON evidence
                         FOR EACH ROW EXECUTE FUNCTION assert_release_app_match();
END IF;

  IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_rs_release_app_guard') THEN
CREATE TRIGGER trg_rs_release_app_guard
    BEFORE INSERT OR UPDATE ON risk_story
                         FOR EACH ROW EXECUTE FUNCTION assert_release_app_match();
END IF;

  -- Evidence tied to a claim must match claim app
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
              ON p.app_id  = lv.app_id
                  AND p.version = lv.max_version;

CREATE OR REPLACE VIEW v_profile_fields AS
SELECT
    pf.profile_id,
    p.app_id,
    p.version,
    pf.field_key,
    pf.value,
    pf.source_system,
    pf.source_ref,
    pf.collected_at
FROM profile p
         JOIN profile_field pf ON pf.profile_id = p.profile_id;
