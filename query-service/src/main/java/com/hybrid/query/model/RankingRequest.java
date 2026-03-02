package com.hybrid.query.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record RankingRequest(
        @JsonProperty("query") String query,
        @JsonProperty("topK") int topK,
        @JsonProperty("candidates") List<SearchResult> candidates
) {
}
