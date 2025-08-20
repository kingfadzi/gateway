#!/usr/bin/env bash
set -euo pipefail

BASE="http://localhost:8080"
APP_OK="${1:-CORR-12356}"          # app that owns the evidence
APP_WRONG="${2:-CORR-12355}"       # different app to test wrong-app case
FIELD_KEY="${3:-app_criticality}"  # underscore keys
URI="${4:-https://confluence/x/y}"

jq >/dev/null 2>&1 || { echo "jq is required"; exit 1; }

step() { echo -e "\n=== $* ==="; }

# --------- Precheck: field exists ----------
step "Precheck: resolve field id for $APP_OK / $FIELD_KEY"
PFID=$(curl -sS "$BASE/internal/apps/$APP_OK/field-id?key=$FIELD_KEY" | jq -r .profileFieldId)
if [[ "$PFID" == "null" || -z "$PFID" ]]; then
  echo "FAIL: fieldKey '$FIELD_KEY' not found for app $APP_OK"; exit 1
fi
echo "profileFieldId: $PFID"

# --------- Make sure we have evidence (Chunk 5) ----------
step "Ensure evidence exists (link evidence + dedup)"
EVID_JSON=$(curl -sS -X POST "$BASE/api/apps/$APP_OK/evidence" \
  -H 'content-type: application/json' \
  -d "{\"fieldKey\":\"$FIELD_KEY\",\"uri\":\"$URI\",\"type\":\"link\"}")
echo "$EVID_JSON" | jq .
EVID=$(echo "$EVID_JSON" | jq -r .evidenceId)
if [[ -z "$EVID" || "$EVID" == "null" ]]; then
  echo "FAIL: Could not create or resolve evidenceId"; exit 1
fi
echo "evidenceId: $EVID"

# --------- Happy path claim ----------
step "Submit claim (happy path)"
CLAIM_OK=$(curl -sS -X POST "$BASE/api/apps/$APP_OK/claims" \
  -H 'content-type: application/json' \
  -d "{
        \"requirementId\":\"POLICY-REQ-001\",
        \"releaseId\":\"REL-001\",
        \"evidenceId\":\"$EVID\",
        \"method\":\"manual\",
        \"profileFieldExpected\":\"$FIELD_KEY\",
        \"typeExpected\":\"link\",
        \"releaseWindowStart\":\"2025-09-01T10:00:00Z\"
      }")
echo "$CLAIM_OK" | jq .
CLAIM_ID_OK=$(echo "$CLAIM_OK" | jq -r .claimId)
ACC_OK=$(echo "$CLAIM_OK" | jq -r .acceptable)
[[ "$ACC_OK" == "true" ]] && echo "PASS: acceptable=true" || { echo "FAIL: expected acceptable=true"; exit 1; }
echo "claimId: $CLAIM_ID_OK"

# --------- Wrong app ----------
step "Submit claim with wrong app (ownership check)"
CLAIM_BAD_APP=$(curl -sS -X POST "$BASE/api/apps/$APP_WRONG/claims" \
  -H 'content-type: application/json' \
  -d "{
        \"requirementId\":\"POLICY-REQ-001\",
        \"evidenceId\":\"$EVID\",
        \"method\":\"manual\"
      }")
echo "$CLAIM_BAD_APP" | jq .
ACC_BAD_APP=$(echo "$CLAIM_BAD_APP" | jq -r .acceptable)
[[ "$ACC_BAD_APP" == "false" ]] && echo "PASS: wrong-app rejected" || { echo "FAIL: expected wrong-app to be unacceptable"; exit 1; }

# --------- Field mismatch ----------
step "Submit claim with field mismatch"
CLAIM_BAD_FIELD=$(curl -sS -X POST "$BASE/api/apps/$APP_OK/claims" \
  -H 'content-type: application/json' \
  -d "{
        \"requirementId\":\"POLICY-REQ-001\",
        \"evidenceId\":\"$EVID\",
        \"method\":\"manual\",
        \"profileFieldExpected\":\"security_rating\"
      }")
echo "$CLAIM_BAD_FIELD" | jq .
ACC_BAD_FIELD=$(echo "$CLAIM_BAD_FIELD" | jq -r .acceptable)
[[ "$ACC_BAD_FIELD" == "false" ]] && echo "PASS: field mismatch rejected" || { echo "FAIL: expected field mismatch to be unacceptable"; exit 1; }

# --------- Type mismatch ----------
step "Submit claim with type mismatch"
CLAIM_BAD_TYPE=$(curl -sS -X POST "$BASE/api/apps/$APP_OK/claims" \
  -H 'content-type: application/json' \
  -d "{
        \"requirementId\":\"POLICY-REQ-001\",
        \"evidenceId\":\"$EVID\",
        \"method\":\"manual\",
        \"typeExpected\":\"document\"
      }")
echo "$CLAIM_BAD_TYPE" | jq .
ACC_BAD_TYPE=$(echo "$CLAIM_BAD_TYPE" | jq -r .acceptable)
[[ "$ACC_BAD_TYPE" == "false" ]] && echo "PASS: type mismatch rejected" || { echo "FAIL: expected type mismatch to be unacceptable"; exit 1; }

echo -e "\nALL CHECKS PASSED ✅"
