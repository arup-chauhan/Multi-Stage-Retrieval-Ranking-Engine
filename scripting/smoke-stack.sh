#!/usr/bin/env bash
set -euo pipefail

COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-multi-stage-retrieval-ranking-engine}"

echo "[smoke] validating compose config"
docker compose -p "${COMPOSE_PROJECT_NAME}" -f docker-compose.yaml config >/tmp/msrr-compose-config.out

echo "[smoke] checking running containers"
docker compose -p "${COMPOSE_PROJECT_NAME}" -f docker-compose.yaml ps || true

echo "[smoke] running query smoke (will fail until /search is implemented)"
if ./scripting/query-smoke.sh; then
  echo "[smoke] query path is healthy"
else
  echo "[smoke] query path not ready yet (expected during scaffold stage)"
fi
