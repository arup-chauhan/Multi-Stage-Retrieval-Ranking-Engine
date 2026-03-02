package com.hybrid.query.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SearchMeta(
        @JsonProperty("latency_ms") long latencyMs,
        @JsonProperty("fallback_used") boolean fallbackUsed,
        @JsonProperty("trace_id") String traceId
) {
}
