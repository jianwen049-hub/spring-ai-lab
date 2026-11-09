package com.jianwen.springailab.tool;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Payment-like side effects MUST be idempotent.
 * Key = toolName + ":" + idempotencyKey extracted from args (or full args hash).
 * Replays cached result instead of double-charging / double-refunding.
 */
public final class IdempotentToolGuard {
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private final ToolCallback delegate;
    private final String keyField;

    public IdempotentToolGuard(ToolCallback delegate, String keyField) {
        this.delegate = Objects.requireNonNull(delegate);
        this.keyField = keyField == null ? "orderId" : keyField;
    }

    public ToolCallback asTool() {
        return new ToolCallback() {
            @Override public String name() { return delegate.name(); }
            @Override public String description() { return delegate.description(); }
            @Override public String inputSchemaJson() { return delegate.inputSchemaJson(); }

            @Override
            public String call(String argumentsJson) {
                String key = delegate.name() + ":" + extract(argumentsJson, keyField);
                String existing = cache.get(key);
                if (existing != null) {
                    return existing; // replay — no second side effect
                }
                String result = delegate.call(argumentsJson);
                String raced = cache.putIfAbsent(key, result);
                return raced != null ? raced : result;
            }
        };
    }

    /** Tiny JSON string field extractor — no Jackson required for the lab. */
    static String extract(String json, String field) {
        if (json == null) return "null";
        String needle = "\"" + field + "\"";
        int i = json.indexOf(needle);
        if (i < 0) return Integer.toHexString(json.hashCode());
        int colon = json.indexOf(':', i + needle.length());
        if (colon < 0) return Integer.toHexString(json.hashCode());
        int q1 = json.indexOf('"', colon + 1);
        if (q1 < 0) return Integer.toHexString(json.hashCode());
        int q2 = json.indexOf('"', q1 + 1);
        if (q2 < 0) return Integer.toHexString(json.hashCode());
        return json.substring(q1 + 1, q2);
    }

    public int cacheSize() { return cache.size(); }
}
