
# 1) Application Profile Metrics

| Metric                                                | Coverage      | Derived From (tables.fields)                                                    | Notes                 |
| ----------------------------------------------------- | ------------- | ------------------------------------------------------------------------------- | --------------------- |
| App name, BU, function, tier, type                    | Fully Covered | APPLICATION.(name, business\_unit, business\_function, application\_tier, type) | Core metadata.        |
| Repo link, Jira board link                            | Fully Covered | EXTERNAL\_REF(system=gitlab or jira).ref\_key                                   | Reference layer.      |
| Product Owner                                         | Fully Covered | APPLICATION.owner\_id → STAKEHOLDER.display\_name                               | Core + Tracking.      |
| Criticality, Materiality, Operational Status          | Fully Covered | APPLICATION.(criticality, materiality, operational\_status)                     | Core.                 |
| Security, Availability, Integrity, Resilience ratings | Fully Covered | PROFILE(kind=risk-rating) → PROFILE\_FIELD.(key,value,confidence)               | Core profiles.        |
| Governance status (onboarded, in\_progress, pending)  | Fully Covered | APPLICATION.onboarding\_status                                                  | Core field added.     |
| Last audit date and outcome                           | Fully Covered | AUDIT\_EVENT(entity\_type=application).at\_time, outcome                        | Tracking field added. |

---

# 2) Product Owner / Delivery Lead View

| Metric                                     | Coverage      | Derived From (tables.fields)                                                | Notes                                                     |
| ------------------------------------------ | ------------- | --------------------------------------------------------------------------- | --------------------------------------------------------- |
| Release stage and countdown                | Fully Covered | RELEASE.stage, RELEASE.window\_end                                          | Stage optional but supported; countdown uses window\_end. |
| Governance readiness percent               | Fully Covered | ratio of CONTROL\_CLAIM(status=approved) to POLICY\_REQUIREMENT per release | Core join and aggregate.                                  |
| Traffic light (blocked, risks open, ready) | Fully Covered | pending POLICY\_REQUIREMENT, open RISK\_STORY, all claims approved          | Rule-based from Core.                                     |
| Outstanding governance tasks               | Fully Covered | GOVERNANCE\_TASK where status not in (done, rejected)                       | Tracking.                                                 |
| Risk blockers and overdue                  | Fully Covered | RISK\_STORY.status, sla\_due vs now; RISK\_STORY\_HISTORY for escalations   | Core + Tracking.                                          |
| Evidence checklist per release             | Fully Covered | compare POLICY\_REQUIREMENT.required\_evidence\_types to EVIDENCE attached  | Core.                                                     |
| Governance timeline                        | Fully Covered | STAGE\_EVENT ordered by at\_time; AUDIT\_EVENT for significant actions      | Tracking.                                                 |

---

# 3) SME / Control Owner View

| Metric                             | Coverage      | Derived From (tables.fields)                                                               | Notes                              |
| ---------------------------------- | ------------- | ------------------------------------------------------------------------------------------ | ---------------------------------- |
| Assigned SME actions by domain     | Fully Covered | GOVERNANCE\_TASK.assignee\_id and join to POLICY\_REQUIREMENT.domain (via requirement\_id) | Tracking + Core.                   |
| Questionnaire responses and docs   | Fully Covered | PROFILE\_FIELD values; EVIDENCE(uri,type,source\_system)                                   | Core.                              |
| Submit SME feedback or raise risk  | Fully Covered | CONTROL\_CLAIM(comment,status); create RISK\_STORY                                         | Core write path; metric uses Core. |
| SME attestation record (who, when) | Fully Covered | CONTROL\_CLAIM.(submitted\_at, reviewed\_at, reviewed\_by)                                 | Core.                              |
| History and reuse across releases  | Fully Covered | CONTROL\_CLAIM, RISK\_STORY\_HISTORY grouped by APPLICATION                                | Tracking history.                  |

---

# 4) Developer / Engineer View

| Metric                              | Coverage      | Derived From (tables.fields)                                                      | Notes            |
| ----------------------------------- | ------------- | --------------------------------------------------------------------------------- | ---------------- |
| My governance tasks                 | Fully Covered | GOVERNANCE\_TASK.assignee\_id = current user                                      | Tracking.        |
| Evidence requirements checklist     | Fully Covered | POLICY\_REQUIREMENT vs EVIDENCE and CONTROL\_CLAIM                                | Core.            |
| What is blocking release            | Fully Covered | open RISK\_STORY; unapproved CONTROL\_CLAIM                                       | Core.            |
| Timeline expectations and deadlines | Fully Covered | POLICY\_REQUIREMENT.due\_date; GOVERNANCE\_TASK.due\_date; STAGE\_EVENT timeboxes | Core + Tracking. |

---

# 5) Auditor / Compliance Officer View

| Metric                    | Coverage      | Derived From (tables.fields)                                                                                              | Notes            |
| ------------------------- | ------------- | ------------------------------------------------------------------------------------------------------------------------- | ---------------- |
| Audit timeline            | Fully Covered | AUDIT\_EVENT(entity\_type in application, release, requirement, claim, risk, profile, evidence, task) ordered by at\_time | Tracking.        |
| Evidence repository       | Fully Covered | EVIDENCE(type, uri, source\_system, added\_at, valid\_from, valid\_until, revoked\_at)                                    | Core.            |
| Attestation ledger        | Fully Covered | CONTROL\_CLAIM(submitted\_by, submitted\_at, reviewed\_by, reviewed\_at, method, status)                                  | Core.            |
| Exception tracking        | Fully Covered | RISK\_STORY(status, domain, sla\_due) + RISK\_STORY\_HISTORY                                                              | Core + Tracking. |
| Cross-release comparisons | Fully Covered | RELEASE join POLICY\_REQUIREMENT, CONTROL\_CLAIM, RISK\_STORY, AUDIT\_EVENT                                               | Core + Tracking. |

---

# 6) Portfolio / Governance Lead View

| Metric                                                 | Coverage      | Derived From (tables.fields)                                                               | Notes                       |
| ------------------------------------------------------ | ------------- | ------------------------------------------------------------------------------------------ | --------------------------- |
| Applications onboarded percent                         | Fully Covered | count(APPLICATION.onboarding\_status = onboarded) over total applications                  | Core.                       |
| Releases in progress vs blocked                        | Fully Covered | RELEASE counts minus those with pending requirements or open risks                         | Core.                       |
| SME engagement SLA                                     | Fully Covered | CONTROL\_CLAIM.assigned\_at to reviewed\_at; GOVERNANCE\_TASK.created\_at to updated\_at   | Core + Tracking timestamps. |
| Risk hotspot map                                       | Fully Covered | RISK\_STORY grouped (domain, app), frequency from RISK\_STORY\_HISTORY; overlay DEPENDENCY | Core + Tracking.            |
| Throughput trends (time in stage; lead vs deploy time) | Fully Covered | STAGE\_EVENT enter/exit durations; RELEASE.window\_start to window\_end                    | Tracking + Core.            |
| Non-compliant releases                                 | Fully Covered | RELEASE with any POLICY\_REQUIREMENT lacking approved CONTROL\_CLAIM                       | Core.                       |

---

# 7) Advanced and Forward-Looking Metrics

| Metric                              | Coverage      | Derived From (tables.fields)                                                      | Notes                 |
| ----------------------------------- | ------------- | --------------------------------------------------------------------------------- | --------------------- |
| Change velocity by app or component | Fully Covered | count(RELEASE) over period; versions per time unit                                | Core.                 |
| Evidence quality trend              | Fully Covered | PROFILE\_FIELD\_HISTORY.(old\_confidence, new\_confidence, changed\_at)           | Tracking history.     |
| Policy drift impact                 | Fully Covered | POLICY\_REQUIREMENT.policy\_version\_applied over time; compare outcomes pre/post | Core field added.     |
| Reviewer workload and balance       | Fully Covered | CONTROL\_CLAIM.reviewed\_by counts; GOVERNANCE\_TASK.assignee\_id loads           | Core + Tracking.      |
| Last audit outcome and recency      | Fully Covered | max(AUDIT\_EVENT.at\_time) and AUDIT\_EVENT.outcome                               | Tracking field added. |
| Dependency risk propagation         | Fully Covered | DEPENDENCY graph joined with open RISK\_STORY and severity                        | Tracking + Core.      |

---
