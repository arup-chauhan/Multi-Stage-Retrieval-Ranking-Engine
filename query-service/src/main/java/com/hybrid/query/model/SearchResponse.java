package com.hybrid.query.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record SearchResponse(
        @JsonProperty("query") String query,
        @JsonProperty("mode") String mode,
        @JsonProperty("results") List<SearchResult> results,
        @JsonProperty("meta") SearchMeta meta
) {
}
