
---

# Quick Start (env)

```bash
export BASE="http://localhost:8080"
export APP_ID="CORR-12356"
export RELEASE_ID="REL-001"
export WINDOW_START="2025-09-01T10:00:00Z"   # release window start (used as asOf)
```

---

## 1) Create the app (auto-profile)

```bash
curl -sS -X POST "$BASE/api/apps" \
  -H 'Content-Type: application/json' \
  -d "{\"appId\":\"$APP_ID\"}" | jq .
```

---

## 2) Inspect profile & keys (debug helpers)

```bash
# Full profile snapshot
curl -sS "$BASE/api/apps/$APP_ID/profile" | jq .

# Field keys for this app (useful when picking profileField)
curl -sS "$BASE/internal/apps/$APP_ID/field-keys" | jq .
```

---

## 3) Requirements (C2: FE-ready)

```bash
curl -sS "$BASE/api/apps/$APP_ID/requirements?releaseId=$RELEASE_ID&releaseWindowStartIso=$WINDOW_START" | jq .
```

Expected: `{ reviewMode, assessment, domains, requirements[], firedRules, policyVersion }`.

---

## 4) Create Evidence (C5 helper to seed C3)

### 4a) JSON / link evidence

> Supports **dotted** keys (e.g., `security.encryption_at_rest`). The server resolves to the stored key (`encryption_at_rest`) under the latest profile.

```bash
curl -sS -X POST "$BASE/api/apps/$APP_ID/evidence" \
  -H "Content-Type: application/json" \
  -d '{
    "fieldKey":"security.encryption_at_rest",
    "type":"link",
    "uri":"https://confluence/acme/security/encryption-policy-v3.pdf",
    "sourceSystem":"MANUAL",
    "validFrom":"2025-08-01T00:00:00Z"
  }' | tee /tmp/evidence.json | jq .
```

Capture the id:

```bash
export EVID=$(jq -r '.evidenceId' /tmp/evidence.json)
echo "EVID=$EVID"
```

**Dedup check** (same payload → returns existing):

```bash
curl -sS -X POST "$BASE/api/apps/$APP_ID/evidence" \
  -H "Content-Type: application/json" \
  -d '{
    "fieldKey":"security.encryption_at_rest",
    "type":"link",
    "uri":"https://confluence/acme/security/encryption-policy-v3.pdf",
    "sourceSystem":"MANUAL",
    "validFrom":"2025-08-01T00:00:00Z"
  }' | jq .
```

### 4b) Multipart / file evidence (optional)

```bash
export FILE="/path/to/artifact.pdf"

curl -sS -X POST "$BASE/api/apps/$APP_ID/evidence" \
  -H "Content-Type: multipart/form-data" \
  -F 'meta={"profileField":"security.encryption_at_rest","type":"file","sourceSystem":"MANUAL"};type=application/json' \
  -F "file=@${FILE};type=application/pdf" | jq .
```

### 4c) Read back a specific evidence (debug)

```bash
curl -sS "$BASE/internal/evidence/$EVID" | jq .
```

### 4d) List evidence for the app/field (optional)

```bash
curl -sS "$BASE/api/apps/$APP_ID/evidence?fieldKey=security.encryption_at_rest&page=1&pageSize=20" | jq .
```

---

## 5) Reuse Lookup (C3)

> Finds the **best reusable** evidence for a requirement part.
> Accepts dotted keys; internally also tries the flat last segment.

```bash
curl -sS -G "$BASE/internal/evidence/reuse" \
  --data-urlencode "appId=$APP_ID" \
  --data-urlencode "profileField=security.encryption_at_rest" \
  --data-urlencode "maxAgeDays=365" \
  --data-urlencode "asOf=$WINDOW_START" | jq .
```

Expected (when seeded): a `ReuseCandidate` with `evidenceId`, `valid_from/until`, `uri`, `sha256`, etc.
If you get `null`, see “Troubleshooting” below.

---

## 6) (Optional) Raw OPA evaluation (C1 pass-through)

```bash
cat > /tmp/opa-input.json <<JSON
{
  "input": {
    "app": { "id": "$APP_ID" },
    "criticality": "A",
    "security": "A2",
    "integrity": "B",
    "availability": "A",
    "resilience": "2",
    "has_dependencies": true,
    "release": { "id": "$RELEASE_ID", "window_start": "$WINDOW_START" }
  }
}
JSON

curl -sS -X POST "$BASE/internal/policy/evaluate" \
  -H 'Content-Type: application/json' \
  --data-binary @/tmp/opa-input.json | jq .
```

---

## Troubleshooting

* **Reuse returns `null`**

    * Confirm the field key exists:
      `curl -sS "$BASE/internal/apps/$APP_ID/field-keys" | jq .`
    * Make sure **validFrom ≤ asOf** and `status!='revoked'`.
    * Try loosening filters: omit `maxAgeDays`, or temporarily set `asOf=2100-01-01T00:00:00Z`.

* **Create evidence returns `profileFieldKey: null`**
  The service now re-reads by id to include the join; if you still see null, you’re running an older build—rebuild and retry.

---

### Acceptance (what to look for)

* **C2:** requirements endpoint returns FE-ready structure using your stored profile.
* **C3:** reuse call returns a non-null `ReuseCandidate` for a field after creating evidence.
* **Dedup:** posting the same evidence again yields the existing record (same `evidenceId`).
