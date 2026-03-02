package com.hybrid.query.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record VectorSearchResponse(
        @JsonProperty("results") List<VectorSearchResult> results
) {
    public List<VectorSearchResult> resultsOrEmpty() {
        return results == null ? List.of() : results;
    }
}
