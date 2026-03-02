from __future__ import annotations

import math
import re
from typing import Any

from fastapi import FastAPI
from pydantic import BaseModel, Field

app = FastAPI(title="vector-service", version="0.1.0")


class Document(BaseModel):
    doc_id: str
    title: str
    body: str
    source: str
    timestamp: str


class VectorSearchRequest(BaseModel):
    query: str
    topK: int = Field(default=10, ge=1, le=500)
    filters: dict[str, Any] | None = None


class VectorSearchResult(BaseModel):
    doc_id: str
    title: str
    snippet: str
    score: float
    rank: int


class VectorSearchResponse(BaseModel):
    results: list[VectorSearchResult]


CORPUS: list[Document] = [
    Document(doc_id="doc-1", title="1994 FIFA World Cup", body="Brazil won the tournament after a final against Italy.", source="wiki", timestamp="1994-07-17T00:00:00Z"),
    Document(doc_id="doc-2", title="Germany National Team", body="Germany has won multiple football world titles and European championships.", source="wiki", timestamp="2014-07-13T00:00:00Z"),
    Document(doc_id="doc-3", title="World Cup Golden Boot", body="The golden boot is awarded to the top goal scorer in a FIFA World Cup.", source="wiki", timestamp="2022-12-18T00:00:00Z"),
    Document(doc_id="doc-4", title="ScaNN for Vector Search", body="ScaNN is used for efficient approximate nearest neighbor search in large embeddings.", source="blog", timestamp="2021-03-10T00:00:00Z"),
    Document(doc_id="doc-5", title="BM25 Ranking", body="BM25 is a lexical relevance function used by Lucene and Solr.", source="blog", timestamp="2020-11-02T00:00:00Z"),
    Document(doc_id="doc-6", title="Learning to Rank", body="LTR models such as LightGBM improve final search result ordering.", source="docs", timestamp="2023-06-21T00:00:00Z"),
    Document(doc_id="doc-7", title="Hybrid Retrieval Systems", body="Hybrid retrieval combines keyword precision and semantic recall.", source="docs", timestamp="2024-01-14T00:00:00Z"),
    Document(doc_id="doc-8", title="Redis Caching", body="Redis can cache query embeddings and hot results to reduce latency.", source="docs", timestamp="2022-09-01T00:00:00Z"),
]

SYNONYMS: dict[str, set[str]] = {
    "soccer": {"football"},
    "football": {"soccer"},
    "cup": {"tournament"},
    "tournament": {"cup"},
    "ranking": {"reranking", "ltr"},
    "reranking": {"ranking", "ltr"},
}


def tokenize(text: str) -> set[str]:
    return {t for t in re.split(r"[^a-z0-9]+", text.lower()) if t}


def expand_tokens(tokens: set[str]) -> set[str]:
    expanded = set(tokens)
    for token in tokens:
        expanded.update(SYNONYMS.get(token, set()))
    return expanded


def semantic_score(query_tokens: set[str], doc_tokens: set[str]) -> float:
    if not query_tokens or not doc_tokens:
        return 0.0
    overlap = len(query_tokens.intersection(doc_tokens))
    if overlap == 0:
        return 0.0
    return overlap / math.sqrt(len(query_tokens) * len(doc_tokens))


def source_matches(doc: Document, filters: dict[str, Any] | None) -> bool:
    if not filters:
        return True

    source_filter = filters.get("source")
    if source_filter is None:
        return True

    if isinstance(source_filter, str):
        allowed = {source_filter.lower()}
    elif isinstance(source_filter, list):
        allowed = {str(v).lower() for v in source_filter if v is not None}
    else:
        return True

    return doc.source.lower() in allowed


def snippet(text: str, limit: int = 140) -> str:
    if len(text) <= limit:
        return text
    return text[:limit] + "..."


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/search", response_model=VectorSearchResponse)
def search(payload: VectorSearchRequest) -> VectorSearchResponse:
    query_tokens = expand_tokens(tokenize(payload.query.strip()))
    if not query_tokens:
        return VectorSearchResponse(results=[])

    scored: list[tuple[Document, float]] = []
    for doc in CORPUS:
        if not source_matches(doc, payload.filters):
            continue

        doc_tokens = expand_tokens(tokenize(f"{doc.title} {doc.body}"))
        score = semantic_score(query_tokens, doc_tokens)
        if score <= 0.0:
            continue
        scored.append((doc, score))

    scored.sort(key=lambda x: x[1], reverse=True)

    results = [
        VectorSearchResult(
            doc_id=doc.doc_id,
            title=doc.title,
            snippet=snippet(doc.body),
            score=round(score, 6),
            rank=i + 1,
        )
        for i, (doc, score) in enumerate(scored[: payload.topK])
    ]

    return VectorSearchResponse(results=results)
