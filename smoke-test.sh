#!/usr/bin/env bash
# ==============================================================================
# Transporter Assignment API - Smoke Test Script
# Usage: ./smoke-test.sh [BASE_URL]
# Example: ./smoke-test.sh http://localhost:8080
#          ./smoke-test.sh https://transporter-assignment.onrender.com
# ==============================================================================

set -e

BASE_URL="${1:-http://localhost:8080}"
# Strip trailing slash if provided
BASE_URL="${BASE_URL%/}"

echo "=================================================================="
echo " Running Transporter Assignment Smoke Tests against: $BASE_URL"
echo "=================================================================="
echo ""

# Helper to check JSON field using python or grep/sed fallback
extract_json_field() {
    local json="$1"
    local field="$2"
    if command -v python3 >/dev/null 2>&1; then
        echo "$json" | python3 -c "import sys, json; data=json.load(sys.stdin); print(data.get('$field', ''))" 2>/dev/null || echo ""
    elif command -v python >/dev/null 2>&1; then
        echo "$json" | python -c "import sys, json; data=json.load(sys.stdin); print(data.get('$field', ''))" 2>/dev/null || echo ""
    else
        echo "$json" | grep -o "\"$field\":[^,}]*" | head -1 | awk -F':' '{print $2}' | tr -d ' "'
    fi
}

FAILED_TESTS=0

# ------------------------------------------------------------------------------
# 1. Health Check
# ------------------------------------------------------------------------------
echo "--- 1. Health Check (/health) ---"
HEALTH_RESP=$(curl -s -w "\nHTTP_STATUS:%{http_code}" "$BASE_URL/health")
HEALTH_STATUS=$(echo "$HEALTH_RESP" | grep "HTTP_STATUS:" | cut -d':' -f2)
HEALTH_BODY=$(echo "$HEALTH_RESP" | grep -v "HTTP_STATUS:")

echo "HTTP Status Code : $HEALTH_STATUS (Expected: 200)"
echo "Response Body    : $HEALTH_BODY"

if [ "$HEALTH_STATUS" -eq 200 ]; then
    echo "Result           : [PASS]"
else
    echo "Result           : [FAIL]"
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi
echo ""

# ------------------------------------------------------------------------------
# 2. Submit TC2 Input Data
# ------------------------------------------------------------------------------
echo "--- 2. Submit TC2 Input Data (POST /api/v1/transporters/input) ---"
TC2_JSON='{
  "lanes": [
    {"id": 1, "origin": "Chandigarh", "destination": "Shimla"},
    {"id": 2, "origin": "Agra", "destination": "Kanpur"},
    {"id": 3, "origin": "Varanasi", "destination": "Gorakhpur"},
    {"id": 4, "origin": "Amritsar", "destination": "Ludhiana"},
    {"id": 5, "origin": "Coimbatore", "destination": "Madurai"}
  ],
  "transporters": [
    {"id": 1, "name": "Transporter X", "laneQuotes": [{"laneId": 1, "quote": 1000}, {"laneId": 2, "quote": 1500}]},
    {"id": 2, "name": "Transporter Y", "laneQuotes": [{"laneId": 3, "quote": 2000}, {"laneId": 4, "quote": 1800}]},
    {"id": 3, "name": "Transporter Z", "laneQuotes": [{"laneId": 5, "quote": 1200}]},
    {"id": 4, "name": "Transporter W", "laneQuotes": [{"laneId": 1, "quote": 1100}, {"laneId": 4, "quote": 1750}]}
  ]
}'

INPUT_RESP=$(curl -s -w "\nHTTP_STATUS:%{http_code}" -X POST \
  -H "Content-Type: application/json" \
  -d "$TC2_JSON" \
  "$BASE_URL/api/v1/transporters/input")

INPUT_STATUS=$(echo "$INPUT_RESP" | grep "HTTP_STATUS:" | cut -d':' -f2)
INPUT_BODY=$(echo "$INPUT_RESP" | grep -v "HTTP_STATUS:")

echo "HTTP Status Code : $INPUT_STATUS (Expected: 200)"
echo "Response Body    : $INPUT_BODY"

if [ "$INPUT_STATUS" -eq 200 ]; then
    echo "Result           : [PASS]"
else
    echo "Result           : [FAIL]"
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi
echo ""

# ------------------------------------------------------------------------------
# 3. Assignment with K=2 (Expect 422 Infeasible)
# ------------------------------------------------------------------------------
echo "--- 3. Assignment K=2 (POST /api/v1/transporters/assignment) ---"
K2_RESP=$(curl -s -w "\nHTTP_STATUS:%{http_code}" -X POST \
  -H "Content-Type: application/json" \
  -d '{"maxTransporters": 2}' \
  "$BASE_URL/api/v1/transporters/assignment")

K2_STATUS=$(echo "$K2_RESP" | grep "HTTP_STATUS:" | cut -d':' -f2)
K2_BODY=$(echo "$K2_RESP" | grep -v "HTTP_STATUS:")

echo "HTTP Status Code : $K2_STATUS (Expected: 422 Unprocessable Entity)"
echo "Response Body    : $K2_BODY"

if [ "$K2_STATUS" -eq 422 ]; then
    echo "Result           : [PASS] (Infeasible as expected, requires min 3 transporters)"
else
    echo "Result           : [FAIL] (Expected 422, received $K2_STATUS)"
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi
echo ""

# ------------------------------------------------------------------------------
# 4. Assignment with K=3 (Expect Cost 7500)
# ------------------------------------------------------------------------------
echo "--- 4. Assignment K=3 (POST /api/v1/transporters/assignment) ---"
K3_RESP=$(curl -s -w "\nHTTP_STATUS:%{http_code}" -X POST \
  -H "Content-Type: application/json" \
  -d '{"maxTransporters": 3}' \
  "$BASE_URL/api/v1/transporters/assignment")

K3_STATUS=$(echo "$K3_RESP" | grep "HTTP_STATUS:" | cut -d':' -f2)
K3_BODY=$(echo "$K3_RESP" | grep -v "HTTP_STATUS:")
K3_COST=$(extract_json_field "$K3_BODY" "totalCost")

echo "HTTP Status Code : $K3_STATUS (Expected: 200)"
echo "Expected Cost    : 7500"
echo "Actual Cost      : $K3_COST"
echo "Response Body    : $K3_BODY"

if [ "$K3_STATUS" -eq 200 ] && [ "$K3_COST" = "7500" ]; then
    echo "Result           : [PASS] (Matches expected cost 7500)"
else
    echo "Result           : [FAIL] (Expected 200 with cost 7500)"
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi
echo ""

# ------------------------------------------------------------------------------
# 5. Assignment with K=4 (Expect Cost 7450)
# ------------------------------------------------------------------------------
echo "--- 5. Assignment K=4 (POST /api/v1/transporters/assignment) ---"
K4_RESP=$(curl -s -w "\nHTTP_STATUS:%{http_code}" -X POST \
  -H "Content-Type: application/json" \
  -d '{"maxTransporters": 4}' \
  "$BASE_URL/api/v1/transporters/assignment")

K4_STATUS=$(echo "$K4_RESP" | grep "HTTP_STATUS:" | cut -d':' -f2)
K4_BODY=$(echo "$K4_RESP" | grep -v "HTTP_STATUS:")
K4_COST=$(extract_json_field "$K4_BODY" "totalCost")

echo "HTTP Status Code : $K4_STATUS (Expected: 200)"
echo "Expected Cost    : 7450"
echo "Actual Cost      : $K4_COST"
echo "Response Body    : $K4_BODY"

if [ "$K4_STATUS" -eq 200 ] && [ "$K4_COST" = "7450" ]; then
    echo "Result           : [PASS] (Matches expected cost 7450)"
else
    echo "Result           : [FAIL] (Expected 200 with cost 7450)"
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi
echo ""

# ------------------------------------------------------------------------------
# Summary
# ------------------------------------------------------------------------------
echo "=================================================================="
if [ "$FAILED_TESTS" -eq 0 ]; then
    echo " All smoke tests PASSED successfully against $BASE_URL!"
    echo "=================================================================="
    exit 0
else
    echo " $FAILED_TESTS smoke test(s) FAILED."
    echo "=================================================================="
    exit 1
fi

