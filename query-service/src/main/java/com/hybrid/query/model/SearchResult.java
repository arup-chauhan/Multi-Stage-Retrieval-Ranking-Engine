package com.hybrid.query.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public record SearchResult(
        @JsonProperty("doc_id") String docId,
        @JsonProperty("title") String title,
        @JsonProperty("snippet") String snippet,
        @JsonProperty("score") double score,
        @JsonProperty("stage_scores") Map<String, Double> stageScores
) {
}
