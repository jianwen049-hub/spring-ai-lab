package com.jianwen.springailab.chat;

import java.util.List;
import java.util.function.Consumer;

/** Abstraction parallel to Spring AI ChatModel — sync + streaming. */
public interface ChatModel {
    ChatResponse call(List<Message> messages, ChatOptions options);

    /** Stream text chunks (and optionally a final ChatResponse via onComplete). */
    default void stream(List<Message> messages, ChatOptions options,
                        Consumer<String> onChunk, Consumer<ChatResponse> onComplete) {
        ChatResponse r = call(messages, options);
        if (!r.content().isEmpty()) {
            // naive chunking for demos
            String c = r.content();
            int step = Math.max(1, c.length() / 4);
            for (int i = 0; i < c.length(); i += step) {
                onChunk.accept(c.substring(i, Math.min(c.length(), i + step)));
            }
        }
        onComplete.accept(r);
    }
}
