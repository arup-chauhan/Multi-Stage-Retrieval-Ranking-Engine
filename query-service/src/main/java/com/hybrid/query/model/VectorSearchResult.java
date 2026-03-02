package com.hybrid.query.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VectorSearchResult(
        @JsonProperty("doc_id") String docId,
        @JsonProperty("title") String title,
        @JsonProperty("snippet") String snippet,
        @JsonProperty("score") double score,
        @JsonProperty("rank") int rank
) {
}
