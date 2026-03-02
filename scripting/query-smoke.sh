#!/usr/bin/env bash
set -euo pipefail

QUERY_HOST_PORT="${QUERY_HOST_PORT:-8083}"
TARGET_URL="${TARGET_URL:-http://localhost:${QUERY_HOST_PORT}/search}"
QUERY_TEXT="${QUERY_TEXT:-1994 FIFA World Cup}"
TOPK="${TOPK:-5}"

payload=$(cat <<JSON
{"query":"${QUERY_TEXT}","topK":${TOPK},"mode":"hybrid"}
JSON
)

echo "[query-smoke] target=${TARGET_URL}"
status=$(curl -sS -o /tmp/msrr-query-smoke-response.json -w "%{http_code}" \
  -X POST "${TARGET_URL}" \
  -H "Content-Type: application/json" \
  -d "${payload}" || true)

echo "[query-smoke] status=${status}"
cat /tmp/msrr-query-smoke-response.json || true

if [[ "${status}" != "200" ]]; then
  echo "[query-smoke] non-200 response; ensure stack is running and endpoint is implemented." >&2
  exit 1
fi

echo "[query-smoke] success"
