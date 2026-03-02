# Global Makefile for Multi-Stage Retrieval Ranking Engine

SHELL := /bin/bash

COMPOSE_PROJECT_NAME ?= multi-stage-retrieval-ranking-engine
BENCH_REQUESTS ?= 120
BENCH_CONCURRENCY ?= 12
BENCH_TOPK ?= 20
SLO_RATE_QPS ?= 10
SMOKE_RATE_QPS ?= 6
SMOKE_REQUESTS ?= 30
SMOKE_CONCURRENCY ?= 4
BENCH_OUT_BASE ?= docs/benchmarks

.PHONY: build up down clean ps logs ingest query eval smoke benchmark benchmark-docker benchmark-slo-cold benchmark-slo-warm benchmark-slo benchmark-smoke-ci

build:
	docker compose -p $(COMPOSE_PROJECT_NAME) -f docker-compose.yaml build

up:
	docker compose -p $(COMPOSE_PROJECT_NAME) -f docker-compose.yaml up -d --build

down:
	docker compose -p $(COMPOSE_PROJECT_NAME) -f docker-compose.yaml down

clean:
	docker compose -p $(COMPOSE_PROJECT_NAME) -f docker-compose.yaml down -v --remove-orphans

ps:
	docker compose -p $(COMPOSE_PROJECT_NAME) -f docker-compose.yaml ps

logs:
	docker compose -p $(COMPOSE_PROJECT_NAME) -f docker-compose.yaml logs -f --tail=100

ingest:
	@echo "TODO: implement ingestion pipeline command"

query:
	./scripting/query-smoke.sh

eval:
	@echo "TODO: implement offline evaluation command"

smoke:
	./scripting/smoke-stack.sh

benchmark:
	@echo "Running local hybrid benchmark..."
	./scripting/benchmark-hybrid.sh

benchmark-docker:
	@echo "Running benchmark with compose project context..."
	MODE=docker \
	COMPOSE_PROJECT_NAME=$(COMPOSE_PROJECT_NAME) \
	REQUESTS=$(BENCH_REQUESTS) \
	CONCURRENCY=$(BENCH_CONCURRENCY) \
	TOPK=$(BENCH_TOPK) \
	OUT_DIR=$(BENCH_OUT_BASE)/latest \
	./scripting/benchmark-hybrid.sh

benchmark-slo-cold:
	@echo "Running paced SLO cold benchmark (RATE_QPS=$(SLO_RATE_QPS))..."
	MODE=docker \
	COMPOSE_PROJECT_NAME=$(COMPOSE_PROJECT_NAME) \
	REQUESTS=200 \
	CONCURRENCY=20 \
	TOPK=20 \
	RATE_QPS=$(SLO_RATE_QPS) \
	OUT_DIR=$(BENCH_OUT_BASE)/latest-slo-qps$(SLO_RATE_QPS)-cold \
	./scripting/benchmark-hybrid.sh

benchmark-slo-warm:
	@echo "Running paced SLO warm benchmark (RATE_QPS=$(SLO_RATE_QPS))..."
	MODE=docker \
	COMPOSE_PROJECT_NAME=$(COMPOSE_PROJECT_NAME) \
	REQUESTS=200 \
	CONCURRENCY=20 \
	TOPK=20 \
	RATE_QPS=$(SLO_RATE_QPS) \
	OUT_DIR=$(BENCH_OUT_BASE)/latest-slo-qps$(SLO_RATE_QPS)-warm \
	./scripting/benchmark-hybrid.sh

benchmark-slo: benchmark-slo-cold benchmark-slo-warm
	@echo "Completed paced SLO benchmark pair."

benchmark-smoke-ci:
	@echo "Running CI-style paced benchmark smoke check..."
	MODE=docker \
	COMPOSE_PROJECT_NAME=$(COMPOSE_PROJECT_NAME) \
	REQUESTS=$(SMOKE_REQUESTS) \
	CONCURRENCY=$(SMOKE_CONCURRENCY) \
	TOPK=20 \
	RATE_QPS=$(SMOKE_RATE_QPS) \
	OUT_DIR=$(BENCH_OUT_BASE)/ci-smoke \
	./scripting/benchmark-hybrid.sh
