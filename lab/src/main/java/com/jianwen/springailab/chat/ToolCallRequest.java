package com.jianwen.springailab.chat;

import java.util.Objects;

/** Model asks to invoke a named tool with JSON-ish arguments. */
public final class ToolCallRequest {
    private final String id;
    private final String name;
    private final String argumentsJson;

    public ToolCallRequest(String id, String name, String argumentsJson) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.argumentsJson = argumentsJson == null ? "{}" : argumentsJson;
    }

    public String id() { return id; }
    public String name() { return name; }
    public String argumentsJson() { return argumentsJson; }
}
