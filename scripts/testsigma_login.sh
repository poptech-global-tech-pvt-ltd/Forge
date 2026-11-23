#!/bin/bash

set -euo pipefail

COOKIE_JAR=$(mktemp)
trap 'rm -f "$COOKIE_JAR"' EXIT

REDIRECT_TO="https://test-management.testsigma.com/ui/test_cases?generateTestCases=false"
CLIENT_PATH="72987"

read -rp "TestSigma email: " TS_EMAIL
read -rsp "TestSigma password: " TS_PASSWORD
echo

echo "[1/3] Logging in..." >&2
LOGIN_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -c "$COOKIE_JAR" \
  -X POST 'https://id.testsigma.com/login' \
  -H 'accept: application/json, text/plain, */*' \
  -H 'content-type: application/json' \
  --data-raw "{\"username\":\"${TS_EMAIL}\",\"password\":\"${TS_PASSWORD}\"}")

if [ "$LOGIN_STATUS" != "200" ]; then
  echo "Login failed with status $LOGIN_STATUS (captcha may be required — this script can't solve it)." >&2
  exit 1
fi
echo "  -> login OK (SESSION cookie captured)" >&2

echo "[2/3] Fetching authorization token page..." >&2
AUTH_HTML=$(curl -s -b "$COOKIE_JAR" -c "$COOKIE_JAR" \
  "https://id.testsigma.com/callbacks/authorize/${CLIENT_PATH}?redirectTo=$(python3 -c "import urllib.parse,sys; print(urllib.parse.quote(sys.argv[1], safe=''))" "$REDIRECT_TO")")

TOKEN=$(echo "$AUTH_HTML" | grep -oE 'name="token"[^>]*value="[^"]*"' | sed -E 's/.*value="([^"]*)".*/\1/')

if [ -z "$TOKEN" ]; then
  echo "Could not extract token from the authorize page HTML. Dumping HTML for inspection:" >&2
  echo "$AUTH_HTML" >&2
  exit 1
fi
echo "  -> token extracted (${#TOKEN} chars)" >&2

echo "[3/3] Exchanging token for X-TMS-SESSION-ID..." >&2
RESPONSE_HEADERS=$(curl -s -D - -o /dev/null -b "$COOKIE_JAR" \
  -X POST 'https://test-management.testsigma.com/identity/authorize_callback' \
  --data-urlencode "token=${TOKEN}" \
  --data-urlencode "redirectTo=${REDIRECT_TO}")

SESSION_COOKIE=$(echo "$RESPONSE_HEADERS" | grep -i '^set-cookie: X-TMS-SESSION-ID=' | sed -E 's/^[Ss]et-[Cc]ookie: (X-TMS-SESSION-ID=[^;]+).*/\1/')

if [ -z "$SESSION_COOKIE" ]; then
  echo "Could not find X-TMS-SESSION-ID in response headers. Full headers:" >&2
  echo "$RESPONSE_HEADERS" >&2
  exit 1
fi

echo ""
echo "SUCCESS! Fresh session cookie:"
echo "$SESSION_COOKIE"
