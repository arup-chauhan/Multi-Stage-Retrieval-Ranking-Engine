package com.hybrid.query.model;

public record CorpusDocument(
        String docId,
        String title,
        String body,
        String source,
        String timestamp
) {
}
