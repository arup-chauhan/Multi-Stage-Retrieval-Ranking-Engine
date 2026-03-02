package com.hybrid.query.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public record VectorSearchRequest(
        @JsonProperty("query") String query,
        @JsonProperty("topK") int topK,
        @JsonProperty("filters") Map<String, Object> filters
) {
}
