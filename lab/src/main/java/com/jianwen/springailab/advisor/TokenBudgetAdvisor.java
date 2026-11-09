package com.jianwen.springailab.advisor;

import com.jianwen.springailab.chat.ChatOptions;
import com.jianwen.springailab.chat.ChatResponse;
import com.jianwen.springailab.chat.Message;
import com.jianwen.springailab.chat.StubChatModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Hard token budget for a single turn — trims oldest non-system messages first.
 * Mimics production cost / context window guards without a real tokenizer.
 */
public final class TokenBudgetAdvisor implements Advisor {
    private final int budgetTokens;

    public TokenBudgetAdvisor(int budgetTokens) {
        this.budgetTokens = budgetTokens;
    }

    @Override
    public String name() { return "token-budget"; }

    @Override
    public List<Message> before(List<Message> messages, ChatOptions options) {
        List<Message> work = new ArrayList<>(messages);
        while (StubChatModel.estimateTokens(work) > budgetTokens && work.size() > 1) {
            int dropIdx = -1;
            for (int i = 0; i < work.size(); i++) {
                if (work.get(i).role() != Message.Role.SYSTEM) {
                    dropIdx = i;
                    break;
                }
            }
            if (dropIdx < 0) break;
            work.remove(dropIdx);
        }
        return List.copyOf(work);
    }

    @Override
    public ChatOptions beforeOptions(List<Message> messages, ChatOptions options) {
        int used = StubChatModel.estimateTokens(messages);
        int remain = Math.max(64, budgetTokens - used);
        int maxTok = options.maxTokens();
        if (maxTok > remain) {
            return options.withMaxTokens(remain);
        }
        return options;
    }

    @Override
    public ChatResponse after(List<Message> messages, ChatOptions options, ChatResponse response) {
        if (response.totalTokens() > budgetTokens) {
            throw new IllegalStateException("token budget exceeded: " + response.totalTokens()
                    + " > " + budgetTokens);
        }
        return response;
    }
}
