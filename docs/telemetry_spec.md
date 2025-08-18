
# Governance Telemetry Spec 

## Purpose

This document defines all telemetry metrics required for Governance UI personas. Each metric includes: **definition, derivation (tables/fields), and acceptance check**.

---

## 1. Application Profile Metrics

| Metric                  | Definition                                              | Derivation                                                                     | Acceptance Check                                       |
| ----------------------- | ------------------------------------------------------- | ------------------------------------------------------------------------------ | ------------------------------------------------------ |
| App metadata            | Display app attributes (name, BU, function, tier, type) | `APPLICATION.(name, business_unit, business_function, application_tier, type)` | Returns correct values per app\_id                     |
| Repo / Board links      | Show GitLab repo, Jira board refs                       | `EXTERNAL_REF(system=gitlab/jira)`                                             | Links resolve to correct external URLs                 |
| Product Owner           | Owner stakeholder for the app                           | `APPLICATION.owner_id → STAKEHOLDER.display_name`                              | Owner displayed matches stakeholder assignment         |
| Risk ratings            | CIA+R profile values                                    | `PROFILE(kind=risk-rating) → PROFILE_FIELD`                                    | Latest profile snapshot reflects ServiceNow input      |
| Governance status       | Onboarding state of app                                 | `APPLICATION.onboarding_status`                                                | Status updates to onboarded when all prerequisites met |
| Last audit outcome/date | Most recent audit result                                | `AUDIT_EVENT(entity_type=application).outcome, at_time`                        | Shows last recorded audit with timestamp               |

---

## 2. Product Owner / Delivery Lead

| Metric                    | Definition                            | Derivation                                                        | Acceptance Check                                 |
| ------------------------- | ------------------------------------- | ----------------------------------------------------------------- | ------------------------------------------------ |
| Release stage & countdown | Current stage and days to window\_end | `RELEASE.stage, window_end`                                       | Stage shows correctly, countdown ≤ target date   |
| Governance readiness %    | % requirements fulfilled              | ratio of `CONTROL_CLAIM(status=approved)` ÷ `POLICY_REQUIREMENT`  | 100% only when all claims approved               |
| Traffic light             | Governance status indicator           | Red: open blockers, Amber: open risks, Green: all claims approved | Matches expected rules for sample releases       |
| Outstanding tasks         | Governance actions not closed         | `GOVERNANCE_TASK.status≠done`                                     | List matches open tasks in DB                    |
| Risk blockers             | Open risks & overdue                  | `RISK_STORY(status≠closed, sla_due<now)`                          | Risks flagged match expected SLA status          |
| Evidence checklist        | Required vs submitted                 | `POLICY_REQUIREMENT.required_evidence_types` vs `EVIDENCE`        | All required artifacts shown and marked complete |
| Governance timeline       | Stage & audit events                  | `STAGE_EVENT`, `AUDIT_EVENT`                                      | Timeline shows all events in chronological order |

---

## 3. SME / Control Owner

| Metric                       | Definition               | Derivation                                                      | Acceptance Check                                    |
| ---------------------------- | ------------------------ | --------------------------------------------------------------- | --------------------------------------------------- |
| Assigned actions             | SME domain tasks         | `GOVERNANCE_TASK.assignee_id`, join `POLICY_REQUIREMENT.domain` | Assigned SME sees only domain-relevant tasks        |
| Questionnaire responses      | Submitted answers + docs | `PROFILE_FIELD`, `EVIDENCE`                                     | Snapshot matches last submission                    |
| Submit feedback / raise risk | SME input                | new `CONTROL_CLAIM.comment`, new `RISK_STORY`                   | Feedback persists and risk appears in open list     |
| Attestation record           | Who attested and when    | `CONTROL_CLAIM.submitted_at, reviewed_at, reviewed_by`          | Ledger shows all claims with timestamps             |
| History & reuse              | Past SME decisions       | `CONTROL_CLAIM`, `RISK_STORY_HISTORY` by app                    | Historical decisions retrievable for prior releases |

---

## 4. Developer / Engineer

| Metric                | Definition                 | Derivation                                                               | Acceptance Check                          |
| --------------------- | -------------------------- | ------------------------------------------------------------------------ | ----------------------------------------- |
| My tasks              | Tasks for logged-in dev    | `GOVERNANCE_TASK.assignee_id=current_user`                               | Only dev’s tasks shown                    |
| Evidence checklist    | Requirements vs evidence   | `POLICY_REQUIREMENT` vs `EVIDENCE`, `CONTROL_CLAIM`                      | Checklist matches governance requirements |
| Blocking items        | Risks or missing claims    | `RISK_STORY(status≠closed)`, `CONTROL_CLAIM(status≠approved)`            | All blockers displayed                    |
| Timeline expectations | Deadlines for tasks & reqs | `POLICY_REQUIREMENT.due_date`, `GOVERNANCE_TASK.due_date`, `STAGE_EVENT` | Deadlines align with DB values            |

---

## 5. Auditor / Compliance

| Metric                   | Definition                    | Derivation                                                           | Acceptance Check                               |
| ------------------------ | ----------------------------- | -------------------------------------------------------------------- | ---------------------------------------------- |
| Audit timeline           | Chronological actions         | `AUDIT_EVENT` by at\_time                                            | Matches chronological order of recorded events |
| Evidence repository      | All artifacts, filterable     | `EVIDENCE(type, source_system, added_at, revoked_at)`                | Repository lists complete artifact set         |
| Attestation ledger       | Who/what/when for claims      | `CONTROL_CLAIM`                                                      | Ledger accurate for audit sampling             |
| Exception tracking       | Open and closed exceptions    | `RISK_STORY`, `RISK_STORY_HISTORY`                                   | Exceptions match governance logs               |
| Cross-release comparison | Governance history by release | join `RELEASE`, `POLICY_REQUIREMENT`, `CONTROL_CLAIM`, `AUDIT_EVENT` | Multi-release audit traces consistent          |

---

## 6. Portfolio / Governance Lead

| Metric                          | Definition               | Derivation                                                     | Acceptance Check                        |
| ------------------------------- | ------------------------ | -------------------------------------------------------------- | --------------------------------------- |
| % onboarded apps                | Ratio onboarded apps     | `APPLICATION.onboarding_status`                                | Matches portfolio totals                |
| Releases in progress vs blocked | Portfolio view of status | count(RELEASE) vs blocked due to open reqs/risks               | Counts align with backlog               |
| SME engagement SLA              | Avg time to review       | `CONTROL_CLAIM.assigned_at → reviewed_at`                      | SLA metrics compute correctly           |
| Risk hotspot map                | Recurring risks/domains  | `RISK_STORY`, `RISK_STORY_HISTORY`, `DEPENDENCY`               | Heatmap highlights correct domains/apps |
| Throughput trends               | Lead vs deploy time      | `STAGE_EVENT.enter/exit durations`, `RELEASE.window_start/end` | Charts show realistic durations         |
| Non-compliant releases          | Missing attestations     | RELEASE where any `POLICY_REQUIREMENT` lacks approved claim    | Matches governance rules                |

---

## 7. Advanced / Extended

| Metric                      | Definition              | Derivation                                                           | Acceptance Check                       |
| --------------------------- | ----------------------- | -------------------------------------------------------------------- | -------------------------------------- |
| Change velocity             | Release frequency       | count(RELEASE) / time                                                | Trend matches historical releases      |
| Evidence quality trend      | Confidence changes      | `PROFILE_FIELD_HISTORY`                                              | Confidence history plotted correctly   |
| Policy drift                | Req vs applied version  | `POLICY_REQUIREMENT.policy_version_applied`                          | Drift alerts fire when version changes |
| Reviewer workload           | Distribution of reviews | counts of `CONTROL_CLAIM.reviewed_by`, `GOVERNANCE_TASK.assignee_id` | Workload distribution matches logs     |
| Last audit outcome          | Most recent result      | `AUDIT_EVENT.outcome`                                                | Displays last recorded audit result    |
| Dependency risk propagation | Risk spread via deps    | `DEPENDENCY` joined to open `RISK_STORY`                             | Risks show up on dependent apps        |

---

