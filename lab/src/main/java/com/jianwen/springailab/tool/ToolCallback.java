package com.jianwen.springailab.tool;

/** Named tool with JSON-schema-ish args → JSON-ish result. */
public interface ToolCallback {
    String name();
    String description();
    /** JSON Schema draft subset as string for demos. */
    String inputSchemaJson();
    String call(String argumentsJson);
}
