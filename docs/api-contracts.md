# API Contracts (Draft)

## Public API (`query-service`)
`POST /search`

Request (draft):
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

Response (draft):
```json
{
  "query": "1994 FIFA World Cup",
  "results": [
    {
      "doc_id": "doc-1",
      "score": 0.992,
      "stage_scores": {
        "bm25": 8.4,
        "semantic": 0.81,
        "rrf": 0.0123
      }
    }
  ]
}
```
