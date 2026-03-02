# Architecture (Initial)

## Request Path
1. Client calls `query-service /search`
2. `query-service` calls lexical retrieval (Solr)
3. `query-service` calls semantic retrieval (`vector-service`)
4. `query-service` fuses candidates (RRF baseline)
5. Optional rerank via `ranking-service`
6. Return final topK results + stage metadata

## Reliability Baseline
- Semantic retrieval timeout and fallback to lexical-only
- Feature flags for semantic and reranking stages
- Stage-level latency metrics
