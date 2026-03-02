package com.hybrid.query.service;

import com.hybrid.query.model.VectorSearchRequest;
import com.hybrid.query.model.VectorSearchResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class VectorServiceClient {
    private final WebClient client;
    private final long timeoutMs;

    public VectorServiceClient(WebClient.Builder builder,
                               @Value("${vector-service.base-url}") String baseUrl,
                               @Value("${vector-service.timeout-ms}") long timeoutMs) {
        this.client = builder.baseUrl(baseUrl).build();
        this.timeoutMs = timeoutMs;
    }

    public VectorSearchResponse search(String query, int topK, Map<String, Object> filters) {
        VectorSearchResponse response = client.post()
                .uri("/search")
                .bodyValue(new VectorSearchRequest(query, topK, filters))
                .retrieve()
                .bodyToMono(VectorSearchResponse.class)
                .timeout(Duration.ofMillis(timeoutMs))
                .block();

        return response == null ? new VectorSearchResponse(List.of()) : response;
    }
}
