---

# Quick Start (env)

```bash
# Base URL & handy vars
export BASE="http://localhost:8080"
export APP_ID="CORR-12356"
export RELEASE_ID="REL-001"
export WINDOW_START="2025-09-01T10:00:00Z"   # example; use your release window start
```

---

## Step 1 — Create the app (auto-profile)

```bash
curl -sS -X POST "$BASE/api/apps" \
  -H 'Content-Type: application/json' \
  -d "{\"appId\":\"$APP_ID\"}" | jq .
```

Expected: 200 with a profile snapshot (fields like `security_rating`, `availability_rating`, etc).

---

## Step 2 — Inspect the profile (debug helpers)

### 2a) Full profile payload

```bash
curl -sS "$BASE/api/apps/$APP_ID/profile" | jq .
```

### 2b) List available profile field keys

```bash
curl -sS "$BASE/internal/apps/$APP_ID/field-keys" | jq .
```

### 2c) (Optional) Resolve a field key → field ID

```bash
curl -sS "$BASE/internal/apps/$APP_ID/field-id?key=security.encryption_at_rest" | jq .
```

---

## Step 3 — Requirements (C2: read-only, FE-ready)

```bash
curl -sS "$BASE/api/apps/$APP_ID/requirements?releaseId=$RELEASE_ID&releaseWindowStartIso=$WINDOW_START" | jq .
```

Expected: `{ reviewMode, assessment, domains, requirements[], firedRules, policyVersion }`
(Each requirement has `parts.allOf/anyOf/oneOf`; `dueDateDisplay` shows your `WINDOW_START`.)

---

## Step 4 — (Optional) Direct OPA evaluation (internal)

If you want to see the raw policy decision (C1 pass-through):

```bash
cat > /tmp/opa-input.json <<'JSON'
{
  "input": {
    "app": { "id": "CORR-12356" },
    "criticality": "A",
    "security": "A2",
    "integrity": "B",
    "availability": "A",
    "resilience": "2",
    "has_dependencies": true,
    "release": { "id": "REL-001", "window_start": "2025-09-01T10:00:00Z" }
  }
}
JSON

curl -sS -X POST "$BASE/internal/policy/evaluate" \
  -H 'Content-Type: application/json' \
  --data-binary @/tmp/opa-input.json | jq .
```

---

## Step 5 — Evidence Reuse Lookup (C3)

> Returns the **best reusable evidence** for a specific `profileField` as of a timestamp.
> Note: will return `null` until you have at least one evidence row for that field.

```bash
curl -sS -G "$BASE/internal/evidence/reuse" \
  --data-urlencode "appId=$APP_ID" \
  --data-urlencode "profileField=security.encryption_at_rest" \
  --data-urlencode "maxAgeDays=365" \
  --data-urlencode "asOf=$WINDOW_START" | jq .
```

If you prefer “now”:

```bash
curl -sS -G "$BASE/internal/evidence/reuse" \
  --data-urlencode "appId=$APP_ID" \
  --data-urlencode "profileField=security.encryption_at_rest" \
  --data-urlencode "asOf=$(date -u +%Y-%m-%dT%H:%M:%SZ)" | jq .
```

**Expected shape (when evidence exists):**

```json
{
  "evidenceId": "ev_...",
  "valid_from": "2025-08-01T00:00:00Z",
  "valid_until": null,
  "confidence": null,
  "method": null,
  "uri": "https://…/artifact.pdf",
  "sha256": "…",
  "type": "link",
  "source_system": "MANUAL",
  "created_at": "2025-08-05T12:34:56Z"
}
```

---

## (Optional) Step 5a — Seed evidence so C3 returns a hit

If your **create evidence API** is implemented:

```bash
# JSON (link) example
curl -sS -X POST "$BASE/api/apps/$APP_ID/evidence" \
  -H 'Content-Type: application/json' \
  -d '{
    "profileField":"security.encryption_at_rest",
    "type":"link",
    "uri":"https://confluence/acme/security/encryption-policy-v3.pdf",
    "valid_from":"2025-08-01T00:00:00Z",
    "note":"seed for C3 test"
  }' | jq .
```

Then re-run the **Step 5** reuse lookup.

> If the evidence API isn’t live yet, insert a row directly in DB (one-off seed), then re-run Step 5.

---

## (Optional) Debug a specific evidence record

```bash
curl -sS "$BASE/internal/evidence/ev_xxxxxxxx" | jq .
```

---
