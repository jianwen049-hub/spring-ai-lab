package com.jianwen.springailab.chat;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class ChatResponse {
    private final String content;
    private final int promptTokens;
    private final int completionTokens;
    private final List<ToolCallRequest> toolCalls;

    public ChatResponse(String content, int promptTokens, int completionTokens,
                        List<ToolCallRequest> toolCalls) {
        this.content = content == null ? "" : content;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public String content() { return content; }
    public int promptTokens() { return promptTokens; }
    public int completionTokens() { return completionTokens; }
    public int totalTokens() { return promptTokens + completionTokens; }
    public List<ToolCallRequest> toolCalls() { return toolCalls; }
    public boolean hasToolCalls() { return !toolCalls.isEmpty(); }

    public static ChatResponse text(String content, int pt, int ct) {
        return new ChatResponse(content, pt, ct, Collections.emptyList());
    }
}
