# Multi-Stage Retrieval and Ranking Engine

Production-ready hybrid search platform combining BM25 lexical retrieval, semantic ANN retrieval, fusion, and LTR reranking.

## Highlights
- Hybrid retrieval: lexical precision + semantic recall.
- Multi-stage ranking pipeline: candidate generation, fusion, reranking.
- Resilient query orchestration with semantic timeout fallback.
- Service-oriented architecture with independent scaling.
- Local Docker stack and Kubernetes deployment manifests.
- Benchmark and evaluation tooling for latency and relevance.

## Architecture
| Service | Stack | Responsibility | Default Port |
|---|---|---|---|
| `query-service` | Java 21, Spring Boot | Public `/search`, orchestration, fusion, fallback logic | 8083 |
| `vector-service` | Python 3.11, FastAPI | Semantic retrieval over ANN candidate space | 8084 |
| `ranking-service` | Python 3.11, FastAPI | Feature-based reranking (LTR-style scoring) | 8085 |
| `solr` | Solr 9.x | BM25 lexical retrieval | 8983 |
| `redis` | Redis 7.x | Query and embedding cache | 6380 (host) |
| `ollama` | Ollama | Embedding runtime | 11434 |

## Search Pipeline
1. Client sends `POST /search` to `query-service`.
2. Lexical candidates are retrieved from BM25 index.
3. Semantic candidates are retrieved from `vector-service`.
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
