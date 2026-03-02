package com.hybrid.query.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public record SearchRequest(
        @JsonProperty("query") String query,
        @JsonProperty("topK") Integer topK,
        @JsonProperty("mode") String mode,
        @JsonProperty("filters") Map<String, Object> filters
) {
}
