# Multi-Stage Retrieval and Ranking Engine

Production-oriented hybrid search system combining lexical BM25 and semantic ANN retrieval, followed by fusion and Learning-to-Rank (LTR) reranking.

> This README is the v1.0 source of truth for scope, architecture, API contracts, SLOs, and acceptance criteria.

## Resume-Ready Project Summary
Built a microservices-based retrieval stack with Java orchestration and Python ML services, combining Solr BM25 and ANN semantic search, RRF fusion, and feature-based reranking. Implemented reliability controls (timeouts/fallbacks), caching, benchmarking, and autoscaling-ready deployment manifests.

## Objectives
1. Improve relevance over lexical-only search using hybrid retrieval + reranking.
2. Maintain low latency with predictable behavior under load.
3. Demonstrate production-grade engineering signals: observability, fallback behavior, scale readiness, and reproducible evaluation.

## Non-Goals (v1.0)
1. Full online training pipeline with live click feedback loops.
2. Multi-region deployment and cross-region failover.
3. Tenant-aware isolation and hard multi-tenancy policies.

## Core Architecture

### Services
| Service | Language/Runtime | Responsibility | Port |
|---|---|---|---|
| `query-service` | Java 21, Spring Boot | Public `/search`, stage orchestration, fusion, fallback logic | 8083 |
| `vector-service` | Python 3.11 | Embedding + ANN semantic candidate retrieval | 8084 |
| `ranking-service` | Python 3.11 | Feature-based reranking (LTR scoring endpoint) | 8085 |
| `solr` | Solr 9.x | BM25 lexical retrieval | 8983 |
| `redis` | Redis 7.x | Query/embedding/result cache | 6379 container |
| `ollama` | Ollama | Local embedding model runtime | 11434 |

### End-to-End Query Flow
1. Client calls `POST /search` on `query-service`.
2. `query-service` retrieves lexical candidates from Solr (BM25).
3. `query-service` retrieves semantic candidates from `vector-service`.
4. Candidate lists are fused with RRF (and optional weighted blend).
5. Fused shortlist is optionally reranked by `ranking-service`.
6. Final topK results returned with stage-level scores and trace metadata.

## Tech Decisions
1. Java for orchestration (`query-service`) to emphasize reliability patterns and strong API boundaries.
2. Python for ML-heavy services (`vector-service`, `ranking-service`) to keep ANN/LTR iteration fast.
3. Solr for lexical retrieval due to mature BM25 tuning and schema control.
4. RRF as default fusion baseline because it is robust and model-agnostic.
5. Feature flags for safe rollout: `ENABLE_SEMANTIC`, `ENABLE_RERANK`.
6. Docker Compose for local development; Kubernetes manifests for scale signals.

## API Contract

### Public Search API
`POST /search`

Request:
```json
{
  "query": "1994 FIFA World Cup",
  "topK": 10,
  "mode": "hybrid",
  "filters": {
    "source": ["wiki"],
    "timestamp_gte": "1990-01-01T00:00:00Z"
  }
}
```

Response:
```json
{
  "query": "1994 FIFA World Cup",
  "mode": "hybrid",
  "results": [
    {
      "doc_id": "doc-1",
      "title": "1994 FIFA World Cup",
      "score": 0.992,
      "stage_scores": {
        "bm25": 8.4,
        "semantic": 0.81,
        "rrf": 0.0123,
        "ltr": 0.73
      }
    }
  ],
  "meta": {
    "latency_ms": 126,
    "fallback_used": false,
    "trace_id": "c518b0b1-95fc-4dc6-a5bb-d5dc6860ef53"
  }
}
```

### Internal Contracts
1. `vector-service /search`: returns `[{doc_id, score, rank}]`.
2. `ranking-service /rerank`: accepts query + candidates + features, returns reranked candidates.
3. Solr query contract: filtered BM25 topK with field boosts and analyzers.

## Data Model (Document)
Canonical schema in [`data/schemas/document.schema.json`](data/schemas/document.schema.json).

Required fields:
1. `doc_id`
2. `title`
3. `body`
4. `timestamp`
5. `source`

Optional fields:
1. `tags`
2. `metadata`

## Retrieval and Ranking Strategy

### Stage 1: Lexical Retrieval
1. Solr BM25 over `title` + `body`.
2. Field weighting target: `title > body`.
3. Returns lexical topK candidates.

### Stage 2: Semantic Retrieval
1. Query embedding generated via configured embedding model.
2. ANN search over vector index returns semantic topK.
3. Optional metadata filtering with post-filter baseline.

### Stage 3: Fusion
1. Default: Reciprocal Rank Fusion (RRF).
2. Optional: weighted score blending.
3. Candidate union target size: `200-500` before rerank.

### Stage 4: Reranking
1. Features include BM25, semantic score, overlap signals, freshness, and quality features.
2. LTR model: LightGBM ranker.
3. Rerank depth target: `50-200`.

## Reliability, Scale, and Distributed Systems Signals
1. Stateless service design for horizontal scaling.
2. Semantic-stage timeout with lexical-only fallback.
3. Circuit-breaker/retry policy at service client boundaries.
4. Redis cache for hot queries and embedding reuse.
5. Kubernetes deployment + HPA skeleton under `k8s/services/*`.
6. Benchmark scripts for latency/throughput evidence under `scripting/`.

## SLO and Quality Targets (v1.0)

### Performance Targets
1. End-to-end latency: p95 < 200 ms for warm-path hybrid queries.
2. Throughput: stable at target QPS without error spikes.
3. Service error rate: < 1% under benchmark profile.

### Relevance Targets
1. Hybrid fused Recall@K > lexical-only Recall@K.
2. Reranked NDCG@K > fused-only NDCG@K.
3. Ablation report produced for lexical-only vs semantic-only vs fused vs reranked.

## Evaluation Protocol
1. Keep evaluation code in `eval/offline` and benchmark scripts in `eval/benchmarks` / `scripting`.
2. Record run artifacts in `docs/benchmarks/`.
3. Report at least: Recall@K, MRR@K, NDCG@K, p50/p95 latency.

## Repository Layout
- `query-service/`, `vector-service/`, `ranking-service/`: core services.
- `data/`: sample data and schemas.
- `pipelines/`: ingestion and rebuild jobs.
- `eval/`: offline metrics and benchmark helpers.
- `infra/`: docker/solr support files.
- `observability/`: monitoring scaffold.
- `k8s/`: deployment and autoscaling manifests.
- `scripting/`: smoke and benchmark scripts.
- `docs/`: contracts, architecture notes, ADRs.

## Local Development
Prerequisites:
1. Docker + Docker Compose
2. GNU `make`
3. `curl`

Common commands:
```bash
make up
make ps
make logs
make query
make smoke
make benchmark
make down
```

## Environment Variables
Start from [`.env.example`](.env.example):
- `QUERY_HOST_PORT` (default `8083`)
- `VECTOR_HOST_PORT` (default `8084`)
- `RANKING_HOST_PORT` (default `8085`)
- `SOLR_HOST_PORT` (default `8983`)
- `REDIS_HOST_PORT` (default `6380`)
- `OLLAMA_HOST_PORT` (default `11434`)
- `ENABLE_SEMANTIC` (default `true`)
- `ENABLE_RERANK` (default `false`)

## Delivery Plan (Execution Order)
1. Stage A: lexical + semantic retrieval and fused shortlist.
2. Stage B: reranking integration and feature extraction.
3. Stage C: evaluation + benchmark evidence and observability polish.
4. Stage D: deployment hardening (k8s tuning, autoscaling, fallback tests).

## Definition of Done (v1.0)
1. `POST /search` works end-to-end with hybrid mode.
2. Feature-flagged degradation works (`semantic off`, `rerank off`).
3. Benchmark report exists with p50/p95 and error rate.
4. Offline relevance report exists with ablation table.
5. README and docs match actual implementation behavior.

## Implementation Rule
If implementation behavior conflicts with this README, update code to match this README or open an explicit ADR and then update README in the same change.
