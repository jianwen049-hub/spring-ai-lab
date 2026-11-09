package com.jianwen.springailab.chat;

/** Generation knobs — timeout / maxTokens / temperature. */
public final class ChatOptions {
    private final long timeoutMs;
    private final int maxTokens;
    private final double temperature;

    public ChatOptions(long timeoutMs, int maxTokens, double temperature) {
        this.timeoutMs = timeoutMs;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
    }

    public static ChatOptions defaults() {
        return new ChatOptions(30_000L, 1024, 0.2);
    }

    public long timeoutMs() { return timeoutMs; }
    public int maxTokens() { return maxTokens; }
    public double temperature() { return temperature; }

    public ChatOptions withMaxTokens(int n) {
        return new ChatOptions(timeoutMs, n, temperature);
    }
}
