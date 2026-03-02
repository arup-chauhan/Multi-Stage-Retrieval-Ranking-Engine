#!/usr/bin/env bash
set -euo pipefail

MODE="${MODE:-local}"
QUERY_HOST_PORT="${QUERY_HOST_PORT:-8083}"
TARGET_URL="${TARGET_URL:-http://localhost:${QUERY_HOST_PORT}/search}"
QUERY_TEXT="${QUERY_TEXT:-1994 FIFA World Cup}"
REQUESTS="${REQUESTS:-100}"
CONCURRENCY="${CONCURRENCY:-10}"
TOPK="${TOPK:-20}"
RATE_QPS="${RATE_QPS:-0}"
OUT_DIR="${OUT_DIR:-docs/benchmarks/latest}"

mkdir -p "${OUT_DIR}"
LAT_FILE="${OUT_DIR}/latencies_ms.txt"
RES_FILE="${OUT_DIR}/results.txt"
: > "${LAT_FILE}"
: > "${RES_FILE}"

payload=$(cat <<JSON
{"query":"${QUERY_TEXT}","topK":${TOPK},"mode":"hybrid"}
JSON
)

export TARGET_URL LAT_FILE RES_FILE payload RATE_QPS

run_one() {
  local request_id="$1"
  local tmp_file start_ns end_ns latency_ms status

  tmp_file="/tmp/msrr-bench-${request_id}-$$.json"
  start_ns=$(date +%s%N)
  status=$(curl -sS -o "${tmp_file}" -w "%{http_code}" \
    -X POST "${TARGET_URL}" \
    -H "Content-Type: application/json" \
    -d "${payload}" || true)
  end_ns=$(date +%s%N)

  latency_ms=$(( (end_ns - start_ns) / 1000000 ))
  echo "${latency_ms}" >> "${LAT_FILE}"
  echo "request=${request_id} status=${status} latency_ms=${latency_ms}" >> "${RES_FILE}"

  if [[ "${RATE_QPS}" != "0" ]]; then
    sleep "$(awk "BEGIN { printf \"%.4f\", 1/${RATE_QPS} }")"
  fi
}

export -f run_one

printf "Running benchmark mode=%s target=%s requests=%s concurrency=%s\n" \
  "${MODE}" "${TARGET_URL}" "${REQUESTS}" "${CONCURRENCY}"

seq "${REQUESTS}" | xargs -P "${CONCURRENCY}" -I{} bash -c 'run_one "$@"' _ {}

count=$(wc -l < "${LAT_FILE}" | tr -d ' ')
if [[ "${count}" -eq 0 ]]; then
  echo "No latency samples captured." | tee -a "${RES_FILE}"
  exit 1
fi

sorted_file=$(mktemp)
sort -n "${LAT_FILE}" > "${sorted_file}"

p50_idx=$(( (count + 1) / 2 ))
p95_idx=$(( (95 * count + 99) / 100 ))
if [[ "${p95_idx}" -lt 1 ]]; then p95_idx=1; fi
if [[ "${p95_idx}" -gt "${count}" ]]; then p95_idx="${count}"; fi

p50=$(sed -n "${p50_idx}p" "${sorted_file}")
p95=$(sed -n "${p95_idx}p" "${sorted_file}")

{
  echo ""
  echo "summary_count=${count}"
  echo "summary_p50_ms=${p50}"
  echo "summary_p95_ms=${p95}"
} | tee -a "${RES_FILE}"

rm -f "${sorted_file}"

echo "Benchmark output saved to ${OUT_DIR}"
