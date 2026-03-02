package com.hybrid.query.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record RankingResponse(
        @JsonProperty("results") List<SearchResult> results
) {
}
