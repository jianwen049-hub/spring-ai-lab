package com.jianwen.springailab.rag;

import java.util.Map;
import java.util.Objects;

public final class Document {
    private final String id;
    private final String text;
    private final Map<String, String> metadata;

    public Document(String id, String text, Map<String, String> metadata) {
        this.id = Objects.requireNonNull(id);
        this.text = text == null ? "" : text;
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public String id() { return id; }
    public String text() { return text; }
    public Map<String, String> metadata() { return metadata; }
}
