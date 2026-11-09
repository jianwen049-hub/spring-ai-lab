package com.jianwen.springailab.advisor;

import com.jianwen.springailab.chat.ChatOptions;
import com.jianwen.springailab.chat.Message;
import com.jianwen.springailab.rag.Document;
import com.jianwen.springailab.rag.VectorStore;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Injects retrieved context as a system message before the user turn. */
public final class RagAdvisor implements Advisor {
    private final VectorStore store;
    private final int topK;

    public RagAdvisor(VectorStore store, int topK) {
        this.store = store;
        this.topK = topK;
    }

    @Override
    public String name() { return "rag"; }

    @Override
    public List<Message> before(List<Message> messages, ChatOptions options) {
        String query = "";
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).role() == Message.Role.USER) {
                query = messages.get(i).content();
                break;
            }
        }
        List<Document> hits = store.similaritySearch(query, topK);
        if (hits.isEmpty()) return messages;

        String ctx = hits.stream()
                .map(d -> "- [" + d.id() + "] " + d.text())
                .collect(Collectors.joining("\n"));
        Message sys = Message.system("检索上下文（只可引用以下片段，勿编造）：\n" + ctx);

        List<Message> out = new ArrayList<>();
        out.add(sys);
        out.addAll(messages);
        return List.copyOf(out);
    }
}
