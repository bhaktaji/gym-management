#!/usr/bin/env bash
set -euo pipefail

# GymKhata Production Readiness & Staging Smoke Test Script
# Usage: ./smoke-test.sh [BASE_URL] [SUPER_ADMIN_PASSWORD]

BASE_URL="${1:-http://localhost:8080}"
ADMIN_PASSWORD="${2:-ChangeMe@123}"

echo "=========================================================="
echo " GymKhata Smoke Test — Target: ${BASE_URL}"
echo "=========================================================="

# 1. Health & Public Configuration Check
echo -n "[1/7] Testing Public Health Endpoint (/api/v1/config/public)... "
CONFIG_RES=$(curl -s -w "\n%{http_code}" "${BASE_URL}/api/v1/config/public")
CONFIG_CODE=$(echo "$CONFIG_RES" | tail -n 1)
CONFIG_BODY=$(echo "$CONFIG_RES" | sed '$d')

if [ "$CONFIG_CODE" != "200" ]; then
    echo "FAILED (HTTP ${CONFIG_CODE})"
    echo "$CONFIG_BODY"
    exit 1
fi
echo "PASSED (HTTP 200)"

# 2. Super Admin Authentication
echo -n "[2/7] Authenticating Super Admin (/api/v1/auth/login)... "
ADMIN_LOGIN_RES=$(curl -s -X POST "${BASE_URL}/api/v1/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"phone\":\"+910000000000\",\"password\":\"${ADMIN_PASSWORD}\"}")

ADMIN_TOKEN=$(echo "$ADMIN_LOGIN_RES" | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4 || echo "")
if [ -z "$ADMIN_TOKEN" ]; then
    echo "FAILED: Could not retrieve super admin access token."
    echo "$ADMIN_LOGIN_RES"
    exit 1
fi
echo "PASSED"

# 3. Create Gym & Owner Account
RAND_ID=$((RANDOM % 90000 + 10000))
GYM_NAME="Smoke Gym ${RAND_ID}"
OWNER_PHONE="+9198${RAND_ID}000"

echo -n "[3/7] Provisioning Tenant Gym & Single Owner... "
CREATE_GYM_RES=$(curl -s -X POST "${BASE_URL}/api/v1/admin/gyms" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "{\"gymName\":\"${GYM_NAME}\",\"ownerName\":\"Smoke Owner\",\"phone\":\"${OWNER_PHONE}\",\"email\":\"smoke${RAND_ID}@gymkhata.in\",\"password\":\"Passw0rd!23\",\"gstRegistered\":false}")

GYM_ID=$(echo "$CREATE_GYM_RES" | grep -o '"gymId":"[^"]*' | cut -d'"' -f4 || echo "")
if [ -z "$GYM_ID" ]; then
    echo "FAILED to create gym."
    echo "$CREATE_GYM_RES"
    exit 1
fi
echo "PASSED (gymId: ${GYM_ID})"

# 4. Owner Login
echo -n "[4/7] Authenticating Gym Owner... "
OWNER_LOGIN_RES=$(curl -s -X POST "${BASE_URL}/api/v1/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"phone\":\"${OWNER_PHONE}\",\"password\":\"Passw0rd!23\"}")

OWNER_TOKEN=$(echo "$OWNER_LOGIN_RES" | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4 || echo "")
if [ -z "$OWNER_TOKEN" ]; then
    echo "FAILED owner login."
    echo "$OWNER_LOGIN_RES"
    exit 1
fi
echo "PASSED"

# 5. Enrol Member & Enforce 4-digit PIN Generation
echo -n "[5/7] Enrolling Member & Generating 4-digit PIN... "
MEMBER_PHONE="+9197${RAND_ID}111"
MEMBER_RES=$(curl -s -X POST "${BASE_URL}/api/v1/members" \
    -H "Authorization: Bearer ${OWNER_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"Smoke Member\",\"phone\":\"${MEMBER_PHONE}\",\"state\":\"Delhi\",\"whatsappOptIn\":true}")

MEMBER_ID=$(echo "$MEMBER_RES" | grep -o '"id":"[^"]*' | head -n 1 | cut -d'"' -f4 || echo "")
MEMBER_PIN=$(echo "$MEMBER_RES" | grep -o '"generatedPin":"[^"]*' | cut -d'"' -f4 || echo "")

if [ -z "$MEMBER_ID" ] || [ -z "$MEMBER_PIN" ]; then
    echo "FAILED: Member created without ID or PIN."
    echo "$MEMBER_RES"
    exit 1
fi
echo "PASSED (PIN: ${MEMBER_PIN})"

# 6. Enable Attendance & Test QR Self Check-In
echo -n "[6/7] Enabling Attendance & Testing Self Check-In... "
curl -s -X PUT "${BASE_URL}/api/v1/tenant/settings" \
    -H "Authorization: Bearer ${OWNER_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "{\"attendanceEnabled\":true,\"selfCheckoutEnabled\":true}" > /dev/null

GYM_INFO=$(curl -s "${BASE_URL}/api/v1/tenant/gym" -H "Authorization: Bearer ${OWNER_TOKEN}")
GYM_SLUG=$(echo "$GYM_INFO" | grep -o '"slug":"[^"]*' | cut -d'"' -f4 || echo "gym")

CHECKIN_RES=$(curl -s -X POST "${BASE_URL}/api/v1/public/attendance/${GYM_SLUG}/checkin" \
    -H "Content-Type: application/json" \
    -d "{\"phone\":\"${MEMBER_PHONE}\",\"pin\":\"${MEMBER_PIN}\"}")

BADGE=$(echo "$CHECKIN_RES" | grep -o '"badgeColor":"[^"]*' | cut -d'"' -f4 || echo "")
if [ -z "$BADGE" ]; then
    echo "FAILED check-in."
    echo "$CHECKIN_RES"
    exit 1
fi
echo "PASSED (Badge: ${BADGE})"

# 7. Create Plan, Enrol, and Verify Invoice PDF
echo -n "[7/7] Generating Plan Invoice & Verifying PDF (%PDF signature)... "
PLAN_RES=$(curl -s -X POST "${BASE_URL}/api/v1/plans" \
    -H "Authorization: Bearer ${OWNER_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"Monthly Smoke Plan\",\"durationDays\":30,\"pricePaise\":150000}")
PLAN_ID=$(echo "$PLAN_RES" | grep -o '"id":"[^"]*' | head -n 1 | cut -d'"' -f4)

ENROL_RES=$(curl -s -X POST "${BASE_URL}/api/v1/memberships/enrol" \
    -H "Authorization: Bearer ${OWNER_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "{\"memberId\":\"${MEMBER_ID}\",\"planId\":\"${PLAN_ID}\"}")
MS_ID=$(echo "$ENROL_RES" | grep -o '"id":"[^"]*' | head -n 1 | cut -d'"' -f4)

INV_RES=$(curl -s -X POST "${BASE_URL}/api/v1/invoices/generate/${MS_ID}" \
    -H "Authorization: Bearer ${OWNER_TOKEN}")
INV_ID=$(echo "$INV_RES" | grep -o '"id":"[^"]*' | head -n 1 | cut -d'"' -f4)

PDF_HEADER=$(curl -s "${BASE_URL}/api/v1/invoices/${INV_ID}/pdf" \
    -H "Authorization: Bearer ${OWNER_TOKEN}" | head -c 4 || echo "")

if [ "$PDF_HEADER" != "%PDF" ]; then
    echo "FAILED: Invalid PDF header '${PDF_HEADER}'"
    exit 1
fi
echo "PASSED (%PDF verified)"

echo "=========================================================="
echo " ALL 7 SMOKE TESTS COMPLETED SUCCESSFULLY!"
echo "=========================================================="
