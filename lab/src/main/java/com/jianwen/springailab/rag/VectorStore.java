package com.jianwen.springailab.rag;

import java.util.List;

/** Spring AI VectorStore-shaped abstraction. */
public interface VectorStore {
    void add(List<Document> documents);
    List<Document> similaritySearch(String query, int topK);
}
