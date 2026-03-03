# Multi-Stage Retrieval and Ranking Engine

A distributed hybrid retrieval engine that combines Apache Solr BM25 lexical retrieval with ScaNN-based ANN semantic retrieval, then reranks results using LightGBM Learning-to-Rank (LTR).

## Project Successor
This repository is a focused successor to [Hybrid-Retrieval-and-Ranking-Engine](https://github.com/arup-chauhan/Hybrid-Retrieval-and-Ranking-Engine).  
The earlier project emphasized broader service decomposition; this one centers on multi-stage retrieval quality, ranking effectiveness, and cleaner evaluation.

## Highlights
- Hybrid retrieval: lexical precision + semantic recall.
- ScaNN ANN candidate generation for high-recall semantic retrieval.
- Multi-stage ranking pipeline: candidate generation, fusion, reranking.
- Resilient query orchestration with semantic timeout fallback.
- Service-oriented architecture with independent scaling.
- Local Docker stack and Kubernetes deployment manifests.
- Benchmark and evaluation tooling for latency and relevance.

## Architecture
| Service | Stack | Responsibility | Default Port |
|---|---|---|---|
| `query-service` | Java 21, Spring Boot | Public `/search`, orchestration, fusion, fallback logic | 8083 |
| `vector-service` | Python 3.11, FastAPI | ScaNN-based semantic ANN candidate retrieval | 8084 |
| `ranking-service` | Python 3.11, FastAPI | Feature-based reranking using LightGBM LTR | 8085 |
| `solr` | Solr 9.x | BM25 lexical retrieval | 8983 |
| `redis` | Redis 7.x | Query and embedding cache | 6380 (host) |
| `ollama` | Ollama | Embedding runtime | 11434 |

## ScaNN Integration (Explicit)
- ScaNN is used as an ANN library inside `vector-service` (Python), not as a standalone database.
- Apache Solr remains the primary system of record for document fields and metadata filtering.
- Ingestion pipeline generates embeddings, builds ScaNN index artifacts, and stores `vector_row -> doc_id` mappings.
- `vector-service` loads the ScaNN index into memory at startup and serves topK ANN candidates at query time.
- Metadata constraints are applied as post-filter or hybrid filter logic after ANN candidate retrieval.

## Search Pipeline
1. Client sends `POST /search` to `query-service`.
2. Lexical candidates are retrieved from BM25 index.
3. ScaNN semantic candidates are retrieved from `vector-service`.
4. Candidate lists are fused using Reciprocal Rank Fusion (RRF).
5. Final shortlist is reranked via `ranking-service` (when enabled).
6. Ranked results are returned with stage-level score metadata.

## API
### `POST /search`
Request:
```json
{
  "query": "1994 FIFA World Cup",
  "topK": 10,
  "mode": "hybrid",
  "filters": {
    "source": ["wiki"]
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

Example:
```bash
curl -s -X POST http://localhost:8083/search \
  -H "Content-Type: application/json" \
  -d '{"query":"1994 FIFA World Cup","topK":5,"mode":"hybrid"}'
```

## Production Signals
- Explicit multi-stage ranking with per-stage score visibility (`bm25`, `semantic`, `rrf`, `ltr`).
- Deterministic degradation path: semantic timeout or error falls back to lexical path.
- Runtime feature controls for safe rollout: `ENABLE_SEMANTIC`, `ENABLE_RERANK`.
- Bounded candidate and rerank depth to keep latency predictable.
- Reproducible benchmark harness and artifact capture for regression checks.

## Reliability and Failure Behavior
- If `vector-service` is slow/unavailable, `query-service` returns lexical-only results without failing the request.
- If `ranking-service` is unavailable, fused candidates are returned directly.
- Stage-level timeout budgets are enforced from `query-service` for downstream calls.
- `/health` endpoints are exposed by query/vector/ranking services for orchestrator probes.
- Retry and circuit-breaker policy can be enabled at gateway/service-client boundaries in k8s deployments.

## Performance Profile
Target envelope:
- End-to-end hybrid search: sub-200 ms p95 on warm path.
- Stable throughput under concurrent load with bounded error rate.
- Consistent tail latency through caching and bounded rerank depth.

Measurement workflow:
- Run `make benchmark` for local performance snapshot.
- Run `make benchmark-slo` for paced cold/warm profile.
- Store and compare benchmark outputs under `docs/benchmarks/`.

## Scale Model
- `query-service`, `vector-service`, and `ranking-service` are stateless and horizontally scalable.
- Service replicas can scale independently based on stage-specific load.
- Kubernetes manifests include HPA resources under `k8s/services/*/hpa.yaml`.
- Redis is used for hot-query and embedding cache paths to reduce repeated compute.

## Relevance and Evaluation Signals
- Offline evaluation supports Recall@K, MRR@K, and NDCG@K comparisons.
- Ablation protocol compares lexical-only, semantic-only, fused, and reranked paths.
- Relevance improvements are validated alongside latency impact before rollout.

## Security Baseline
- Internal service communication is intended for private network boundaries.
- Configuration is externalized via environment variables for secret-safe deployment.
- Production hardening supports gateway auth, mTLS/service mesh policy, and least-privilege runtime.

## Quick Start
Prerequisites:
- Docker + Docker Compose
- GNU `make`

Run:
```bash
cp .env.example .env
make build
make up
make ps
make query
```

Stop:
```bash
make down
```

## Configuration
Key environment variables (see [`.env.example`](.env.example)):
- `QUERY_HOST_PORT`
- `VECTOR_HOST_PORT`
- `RANKING_HOST_PORT`
- `SOLR_HOST_PORT`
- `REDIS_HOST_PORT`
- `OLLAMA_HOST_PORT`
- `ENABLE_SEMANTIC`
- `ENABLE_RERANK`

## Observability and Performance
- Stage-level latency and fallback metadata are returned in API responses.
- Benchmark scripts are available under `scripting/`.
- Benchmark artifacts are stored under `docs/benchmarks/`.

Run benchmark:
```bash
make benchmark
```

## Deployment
- Local container deployment: `docker-compose.yaml`.
- Kubernetes manifests with HPA configuration: `k8s/`.

## Repository Layout
- `query-service/`, `vector-service/`, `ranking-service/`: core services.
- `data/`: sample data and schema.
- `eval/`: offline relevance and benchmark helpers.
- `scripting/`: smoke tests and benchmark scripts.
- `k8s/`: deployment manifests and autoscaling resources.
- `docs/`: architecture notes, contracts, and ADRs.
