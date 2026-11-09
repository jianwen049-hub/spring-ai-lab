package com.jianwen.springailab.chat;

import com.jianwen.springailab.advisor.Advisor;
import com.jianwen.springailab.tool.ToolCallback;
import com.jianwen.springailab.tool.ToolRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Spring AI ChatClient-shaped facade: advisors → model → optional tool loop.
 */
public final class ChatClient {
    private final ChatModel model;
    private final List<Advisor> advisors;
    private final ToolRegistry tools;
    private final int maxToolRounds;

    private ChatClient(ChatModel model, List<Advisor> advisors, ToolRegistry tools, int maxToolRounds) {
        this.model = Objects.requireNonNull(model);
        this.advisors = List.copyOf(advisors);
        this.tools = tools == null ? new ToolRegistry() : tools;
        this.maxToolRounds = maxToolRounds;
    }

    public static Builder builder(ChatModel model) {
        return new Builder(model);
    }

    public ChatResponse prompt(String userText) {
        return prompt(List.of(Message.user(userText)), ChatOptions.defaults());
    }

    public ChatResponse prompt(List<Message> messages, ChatOptions options) {
        List<Message> msgs = new ArrayList<>(messages);
        ChatOptions opts = options == null ? ChatOptions.defaults() : options;

        for (Advisor a : advisors) {
            msgs = new ArrayList<>(a.before(msgs, opts));
            opts = a.beforeOptions(msgs, opts);
        }

        ChatResponse response = model.call(msgs, opts);
        int rounds = 0;
        while (response.hasToolCalls() && rounds < maxToolRounds) {
            rounds++;
            for (ToolCallRequest tc : response.toolCalls()) {
                ToolCallback tool = tools.get(tc.name())
                        .orElseThrow(() -> new IllegalStateException("unknown tool: " + tc.name()));
                String result = tool.call(tc.argumentsJson());
                msgs.add(Message.assistant("tool_call:" + tc.name()));
                msgs.add(Message.tool(tc.name(), result));
            }
            response = model.call(msgs, opts);
        }

        for (Advisor a : advisors) {
            response = a.after(msgs, opts, response);
        }
        return response;
    }

    public void streamPrompt(String userText, Consumer<String> onChunk, Consumer<ChatResponse> onComplete) {
        List<Message> msgs = new ArrayList<>();
        msgs.add(Message.user(userText));
        ChatOptions opts = ChatOptions.defaults();
        for (Advisor a : advisors) {
            msgs = new ArrayList<>(a.before(msgs, opts));
            opts = a.beforeOptions(msgs, opts);
        }
        final List<Message> finalMsgs = List.copyOf(msgs);
        final ChatOptions finalOpts = opts;
        model.stream(finalMsgs, finalOpts, onChunk, resp -> {
            ChatResponse r = resp;
            for (Advisor a : advisors) {
                r = a.after(finalMsgs, finalOpts, r);
            }
            onComplete.accept(r);
        });
    }

    public static final class Builder {
        private final ChatModel model;
        private final List<Advisor> advisors = new ArrayList<>();
        private ToolRegistry tools = new ToolRegistry();
        private int maxToolRounds = 3;

        private Builder(ChatModel model) { this.model = model; }

        public Builder defaultAdvisors(Advisor... as) {
            for (Advisor a : as) advisors.add(a);
            return this;
        }

        public Builder tools(ToolRegistry registry) {
            this.tools = registry;
            return this;
        }

        public Builder maxToolRounds(int n) {
            this.maxToolRounds = n;
            return this;
        }

        public ChatClient build() {
            return new ChatClient(model, advisors, tools, maxToolRounds);
        }
    }
}
