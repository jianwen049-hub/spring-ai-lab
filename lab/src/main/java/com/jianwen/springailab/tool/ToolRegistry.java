package com.jianwen.springailab.tool;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class ToolRegistry {
    private final Map<String, ToolCallback> tools = new LinkedHashMap<>();

    public ToolRegistry register(ToolCallback tool) {
        tools.put(tool.name(), tool);
        return this;
    }

    public Optional<ToolCallback> get(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public Collection<ToolCallback> all() {
        return tools.values();
    }
}
