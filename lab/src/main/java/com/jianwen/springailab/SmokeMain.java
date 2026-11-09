package com.jianwen.springailab;

import com.jianwen.springailab.advisor.LoggingAdvisor;
import com.jianwen.springailab.advisor.RagAdvisor;
import com.jianwen.springailab.advisor.TokenBudgetAdvisor;
import com.jianwen.springailab.chat.ChatClient;
import com.jianwen.springailab.chat.ChatResponse;
import com.jianwen.springailab.chat.StubChatModel;
import com.jianwen.springailab.rag.InMemoryVectorStore;
import com.jianwen.springailab.stream.SseStreamer;
import com.jianwen.springailab.structured.RefundDecision;
import com.jianwen.springailab.structured.StructuredOutputParser;
import com.jianwen.springailab.tool.IdempotentToolGuard;
import com.jianwen.springailab.tool.RefundTool;
import com.jianwen.springailab.tool.ToolRegistry;

/** Offline smoke entry — no Spring Boot, no API key. */
public final class SmokeMain {
    public static void main(String[] args) {
        StubChatModel model = new StubChatModel();
        LoggingAdvisor logging = new LoggingAdvisor();
        InMemoryVectorStore store = InMemoryVectorStore.demo();

        RefundTool refund = new RefundTool();
        IdempotentToolGuard guard = new IdempotentToolGuard(refund, "orderId");
        ToolRegistry registry = new ToolRegistry().register(guard.asTool());

        ChatClient client = ChatClient.builder(model)
                .defaultAdvisors(
                        new RagAdvisor(store, 2),
                        new TokenBudgetAdvisor(4096),
                        logging)
                .tools(registry)
                .maxToolRounds(2)
                .build();

        // 1) plain echo
        ChatResponse r1 = client.prompt("你好，支付通道探测");
        assertTrue(r1.content().contains("stub-ok"), "echo");

        // 2) structured
        ChatResponse r2 = client.prompt("请给出结构化退款决策 JSON");
        RefundDecision d = new StructuredOutputParser().parseRefundDecision(r2.content());
        assertTrue(d.approve, "structured approve");

        // 3) tool call + idempotency
        ChatResponse r3 = client.prompt("用户申请退款，订单 ORD-2024-881，金额 19.90");
        // after tool loop stub returns text; side effect must run once even if we call tool twice
        String once = registry.get("refund_order").orElseThrow().call(
                "{\"orderId\":\"ORD-2024-881\",\"amountCents\":1990}");
        String twice = registry.get("refund_order").orElseThrow().call(
                "{\"orderId\":\"ORD-2024-881\",\"amountCents\":1990}");
        assertTrue(once.equals(twice), "idempotent replay");
        assertTrue(refund.invocations() == 1, "single side effect, got " + refund.invocations());

        // 4) SSE stream
        SseStreamer sse = new SseStreamer();
        client.streamPrompt("STREAM_DEMO 查询到账", sse.chunkSink(), resp -> {
            sse.complete("{\"tokens\":" + resp.completionTokens() + "}");
        });
        assertTrue(sse.asBody().contains("event: token"), "sse token");
        assertTrue(sse.asBody().contains("event: done"), "sse done");

        // 5) RAG retrieval non-empty
        assertTrue(!store.similaritySearch("退款多久到账", 3).isEmpty(), "rag hits");

        // 6) logging advisor recorded
        assertTrue(!logging.entries().isEmpty(), "logging entries");

        System.out.println("smoke_check OK");
    }

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) throw new AssertionError("ASSERT: " + msg);
    }
}
