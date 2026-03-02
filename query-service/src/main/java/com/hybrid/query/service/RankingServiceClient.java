package com.hybrid.query.service;

import com.hybrid.query.model.RankingRequest;
import com.hybrid.query.model.RankingResponse;
import com.hybrid.query.model.SearchResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

@Service
public class RankingServiceClient {
    private final WebClient client;
    private final long timeoutMs;

    public RankingServiceClient(WebClient.Builder builder,
                                @Value("${ranking-service.base-url}") String baseUrl,
                                @Value("${ranking-service.timeout-ms}") long timeoutMs) {
        this.client = builder.baseUrl(baseUrl).build();
        this.timeoutMs = timeoutMs;
    }

    public List<SearchResult> rerank(String query, List<SearchResult> candidates, int topK) {
        RankingResponse response = client.post()
                .uri("/rerank")
                .bodyValue(new RankingRequest(query, topK, candidates))
                .retrieve()
                .bodyToMono(RankingResponse.class)
                .timeout(Duration.ofMillis(timeoutMs))
                .block();

        return response == null || response.results() == null ? List.of() : response.results();
    }
}
