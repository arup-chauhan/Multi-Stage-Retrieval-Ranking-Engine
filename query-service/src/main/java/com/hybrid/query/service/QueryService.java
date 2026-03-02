package com.hybrid.query.service;

import com.hybrid.query.model.CorpusDocument;
import com.hybrid.query.model.SearchMeta;
import com.hybrid.query.model.SearchRequest;
import com.hybrid.query.model.SearchResponse;
import com.hybrid.query.model.SearchResult;
import com.hybrid.query.model.VectorSearchResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class QueryService {
    private static final int RRF_K = 60;

    private final VectorServiceClient vectorServiceClient;
    private final RankingServiceClient rankingServiceClient;
    private final boolean enableSemantic;
    private final boolean enableRerank;

    private final List<CorpusDocument> corpus = List.of(
            new CorpusDocument("doc-1", "1994 FIFA World Cup", "Brazil won the tournament after a final against Italy.", "wiki", "1994-07-17T00:00:00Z"),
            new CorpusDocument("doc-2", "Germany National Team", "Germany has won multiple football world titles and European championships.", "wiki", "2014-07-13T00:00:00Z"),
            new CorpusDocument("doc-3", "World Cup Golden Boot", "The golden boot is awarded to the top goal scorer in a FIFA World Cup.", "wiki", "2022-12-18T00:00:00Z"),
            new CorpusDocument("doc-4", "ScaNN for Vector Search", "ScaNN is used for efficient approximate nearest neighbor search in large embeddings.", "blog", "2021-03-10T00:00:00Z"),
            new CorpusDocument("doc-5", "BM25 Ranking", "BM25 is a lexical relevance function used by Lucene and Solr.", "blog", "2020-11-02T00:00:00Z"),
            new CorpusDocument("doc-6", "Learning to Rank", "LTR models such as LightGBM improve final search result ordering.", "docs", "2023-06-21T00:00:00Z"),
            new CorpusDocument("doc-7", "Hybrid Retrieval Systems", "Hybrid retrieval combines keyword precision and semantic recall.", "docs", "2024-01-14T00:00:00Z"),
            new CorpusDocument("doc-8", "Redis Caching", "Redis can cache query embeddings and hot results to reduce latency.", "docs", "2022-09-01T00:00:00Z")
    );

    public QueryService(VectorServiceClient vectorServiceClient,
                        RankingServiceClient rankingServiceClient,
                        @Value("${feature.enable-semantic}") boolean enableSemantic,
                        @Value("${feature.enable-rerank}") boolean enableRerank) {
        this.vectorServiceClient = vectorServiceClient;
        this.rankingServiceClient = rankingServiceClient;
        this.enableSemantic = enableSemantic;
        this.enableRerank = enableRerank;
    }

    public SearchResponse search(SearchRequest request) {
        long start = System.currentTimeMillis();
        String traceId = UUID.randomUUID().toString();

        String query = request.query() == null ? "" : request.query().trim();
        String mode = request.mode() == null || request.mode().isBlank() ? "hybrid" : request.mode().toLowerCase(Locale.ROOT);
        int topK = normalizeTopK(request.topK());

        if (query.isBlank()) {
            return new SearchResponse(query, mode, List.of(), new SearchMeta(0, false, traceId));
        }

        int candidateDepth = Math.max(topK * 3, 50);
        List<SearchResult> lexicalCandidates = lexicalSearch(query, candidateDepth, request.filters());
        List<SearchResult> semanticCandidates = List.of();
        boolean fallbackUsed = false;

        boolean semanticRequested = enableSemantic && !"lexical".equals(mode);
        if (semanticRequested) {
            try {
                semanticCandidates = vectorServiceClient.search(query, candidateDepth, request.filters())
                        .resultsOrEmpty()
                        .stream()
                        .map(this::fromVectorCandidate)
                        .toList();
            } catch (Exception ex) {
                fallbackUsed = true;
            }
        }

        List<SearchResult> fused = reciprocalRankFuse(lexicalCandidates, semanticCandidates);
        List<SearchResult> preRerank = fused.stream().limit(Math.max(topK * 2, topK)).toList();

        List<SearchResult> finalResults = preRerank.stream().limit(topK).toList();
        boolean rerankRequested = enableRerank && !"lexical".equals(mode);
        if (rerankRequested) {
            try {
                List<SearchResult> reranked = rankingServiceClient.rerank(query, preRerank, topK);
                if (!reranked.isEmpty()) {
                    finalResults = reranked.stream().limit(topK).toList();
                }
            } catch (Exception ex) {
                fallbackUsed = true;
            }
        }

        long latencyMs = System.currentTimeMillis() - start;
        return new SearchResponse(query, mode, finalResults, new SearchMeta(latencyMs, fallbackUsed, traceId));
    }

    private SearchResult fromVectorCandidate(VectorSearchResult candidate) {
        Map<String, Double> stageScores = new HashMap<>();
        stageScores.put("semantic", candidate.score());
        return new SearchResult(candidate.docId(), candidate.title(), candidate.snippet(), candidate.score(), stageScores);
    }

    private List<SearchResult> lexicalSearch(String query, int topK, Map<String, Object> filters) {
        Set<String> queryTokens = tokenize(query);
        if (queryTokens.isEmpty()) {
            return List.of();
        }

        String sourceFilter = extractSourceFilter(filters);
        List<SearchResult> scored = new ArrayList<>();

        for (CorpusDocument doc : corpus) {
            if (sourceFilter != null && !sourceFilter.equalsIgnoreCase(doc.source())) {
                continue;
            }

            Set<String> titleTokens = tokenize(doc.title());
            Set<String> bodyTokens = tokenize(doc.body());

            long titleOverlap = titleTokens.stream().filter(queryTokens::contains).count();
            long bodyOverlap = bodyTokens.stream().filter(queryTokens::contains).count();

            double bm25LikeScore = (2.0 * titleOverlap) + bodyOverlap;
            if (bm25LikeScore <= 0.0) {
                continue;
            }

            Map<String, Double> stageScores = new HashMap<>();
            stageScores.put("bm25", bm25LikeScore);
            scored.add(new SearchResult(doc.docId(), doc.title(), buildSnippet(doc.body()), bm25LikeScore, stageScores));
        }

        return scored.stream()
                .sorted(Comparator.comparingDouble(SearchResult::score).reversed())
                .limit(topK)
                .toList();
    }

    private List<SearchResult> reciprocalRankFuse(List<SearchResult> lexical, List<SearchResult> semantic) {
        Map<String, SearchResult> byDocId = new LinkedHashMap<>();
        Map<String, Double> rrfScores = new HashMap<>();

        for (int i = 0; i < lexical.size(); i++) {
            SearchResult candidate = lexical.get(i);
            byDocId.put(candidate.docId(), candidate);
            rrfScores.merge(candidate.docId(), 1.0 / (RRF_K + i + 1), Double::sum);
        }

        for (int i = 0; i < semantic.size(); i++) {
            SearchResult candidate = semantic.get(i);
            byDocId.putIfAbsent(candidate.docId(), candidate);
            rrfScores.merge(candidate.docId(), 1.0 / (RRF_K + i + 1), Double::sum);
        }

        List<SearchResult> fused = new ArrayList<>();
        for (Map.Entry<String, SearchResult> entry : byDocId.entrySet()) {
            String docId = entry.getKey();
            SearchResult base = entry.getValue();

            Map<String, Double> mergedStageScores = new HashMap<>();
            SearchResult lexicalEntry = lexical.stream().filter(r -> r.docId().equals(docId)).findFirst().orElse(null);
            SearchResult semanticEntry = semantic.stream().filter(r -> r.docId().equals(docId)).findFirst().orElse(null);

            if (lexicalEntry != null && lexicalEntry.stageScores() != null) {
                mergedStageScores.putAll(lexicalEntry.stageScores());
            }
            if (semanticEntry != null && semanticEntry.stageScores() != null) {
                mergedStageScores.putAll(semanticEntry.stageScores());
            }

            double rrf = rrfScores.getOrDefault(docId, 0.0);
            mergedStageScores.put("rrf", rrf);

            fused.add(new SearchResult(base.docId(), base.title(), base.snippet(), rrf, mergedStageScores));
        }

        return fused.stream()
                .sorted(Comparator.comparingDouble(SearchResult::score).reversed())
                .collect(Collectors.toList());
    }

    private int normalizeTopK(Integer topK) {
        if (topK == null) {
            return 10;
        }
        return Math.min(Math.max(topK, 1), 200);
    }

    private String extractSourceFilter(Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) {
            return null;
        }
        Object source = filters.get("source");
        if (source instanceof List<?> values && !values.isEmpty() && values.getFirst() != null) {
            return values.getFirst().toString();
        }
        if (source instanceof String sourceStr && !sourceStr.isBlank()) {
            return sourceStr;
        }
        return null;
    }

    private String buildSnippet(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        if (body.length() <= 140) {
            return body;
        }
        return body.substring(0, 140) + "...";
    }

    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }

        return List.of(text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .stream()
                .map(String::trim)
                .filter(token -> !token.isBlank())
                .collect(Collectors.toSet());
    }
}
