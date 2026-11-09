package com.jianwen.springailab.advisor;

import com.jianwen.springailab.chat.ChatOptions;
import com.jianwen.springailab.chat.ChatResponse;
import com.jianwen.springailab.chat.Message;

import java.util.ArrayList;
import java.util.List;

/** Records call shapes for ops / post-mortems — no PII dumping of full bodies by default. */
public final class LoggingAdvisor implements Advisor {
    public static final class Entry {
        public final int messageCount;
        public final int promptTokens;
        public final int completionTokens;
        public final boolean toolCalls;
        public Entry(int messageCount, int promptTokens, int completionTokens, boolean toolCalls) {
            this.messageCount = messageCount;
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
            this.toolCalls = toolCalls;
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    @Override
    public String name() { return "logging"; }

    @Override
    public ChatResponse after(List<Message> messages, ChatOptions options, ChatResponse response) {
        entries.add(new Entry(
                messages.size(),
                response.promptTokens(),
                response.completionTokens(),
                response.hasToolCalls()));
        return response;
    }

    public List<Entry> entries() { return List.copyOf(entries); }
}
