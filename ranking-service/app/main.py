from __future__ import annotations

import re
from typing import Any

from fastapi import FastAPI
from pydantic import BaseModel, Field

app = FastAPI(title="ranking-service", version="0.1.0")


class Candidate(BaseModel):
    doc_id: str
    title: str
    snippet: str = ""
    score: float
    stage_scores: dict[str, float] = Field(default_factory=dict)


class RerankRequest(BaseModel):
    query: str
    topK: int = Field(default=10, ge=1, le=500)
    candidates: list[Candidate] = Field(default_factory=list)


class RerankResponse(BaseModel):
    results: list[Candidate]


def tokenize(text: str) -> set[str]:
    return {t for t in re.split(r"[^a-z0-9]+", text.lower()) if t}


def overlap_score(query: set[str], doc_text: str) -> float:
    if not query:
        return 0.0
    doc_tokens = tokenize(doc_text)
    if not doc_tokens:
        return 0.0
    return len(query.intersection(doc_tokens)) / len(query)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/rerank", response_model=RerankResponse)
def rerank(payload: RerankRequest) -> RerankResponse:
    query_tokens = tokenize(payload.query)
    rescored: list[Candidate] = []

    for candidate in payload.candidates:
        bm25 = float(candidate.stage_scores.get("bm25", 0.0))
        semantic = float(candidate.stage_scores.get("semantic", 0.0))
        bm25_norm = min(1.0, bm25 / 6.0)
        overlap = overlap_score(query_tokens, f"{candidate.title} {candidate.snippet}")

        ltr = 0.5 * bm25_norm + 0.35 * semantic + 0.15 * overlap
        stage_scores = dict(candidate.stage_scores)
        stage_scores["ltr"] = round(ltr, 6)

        rescored.append(
            Candidate(
                doc_id=candidate.doc_id,
                title=candidate.title,
                snippet=candidate.snippet,
                score=round(ltr, 6),
                stage_scores=stage_scores,
            )
        )

    rescored.sort(key=lambda c: c.score, reverse=True)
    return RerankResponse(results=rescored[: payload.topK])
