package com.jianwen.springailab.chat;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Offline fake model. Deterministic rules so smoke/tests need no API key.
 * Triggers:
 *   - user contains "退款" + "ORD-" → emit refund tool call
 *   - user contains "STREAM_DEMO" → long answer for SSE
 *   - otherwise echo a short structured-looking reply
 */
public final class StubChatModel implements ChatModel {

    @Override
    public ChatResponse call(List<Message> messages, ChatOptions options) {
        String lastUser = lastUserContent(messages).toLowerCase(Locale.ROOT);
        int pt = estimateTokens(messages);
        int budget = options == null ? 1024 : options.maxTokens();

        if (lastUser.contains("退款") && lastUser.contains("ord-")) {
            String orderId = extractOrderId(lastUser);
            String args = "{\"orderId\":\"" + orderId + "\",\"amountCents\":1990,\"reason\":\"user_request\"}";
            ToolCallRequest tc = new ToolCallRequest("call_" + UUID.randomUUID(), "refund_order", args);
            return new ChatResponse("", pt, 32, List.of(tc));
        }

        if (lastUser.contains("stream_demo")) {
            String body = "SSE演示：支付单状态查询已受理；风控规则引擎返回通过；"
                    + "预计到账 T+1；如需人工复核请携带 traceId。";
            if (body.length() > budget) {
                body = body.substring(0, budget);
            }
            return ChatResponse.text(body, pt, estimateTokens(body));
        }

        // structured-ish JSON for RefundDecision demos
        if (lastUser.contains("结构化") || lastUser.contains("structured")) {
            String json = "{\"approve\":true,\"reason\":\"risk_pass\",\"riskScore\":12}";
            return ChatResponse.text(json, pt, estimateTokens(json));
        }

        String echo = "stub-ok: " + truncate(lastUserContent(messages), 120);
        return ChatResponse.text(echo, pt, estimateTokens(echo));
    }

    private static String lastUserContent(List<Message> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).role() == Message.Role.USER) {
                return messages.get(i).content();
            }
        }
        return "";
    }

    private static String extractOrderId(String lower) {
        int i = lower.indexOf("ord-");
        if (i < 0) return "ORD-UNKNOWN";
        StringBuilder sb = new StringBuilder();
        for (int j = i; j < lower.length(); j++) {
            char c = lower.charAt(j);
            if (Character.isLetterOrDigit(c) || c == '-') sb.append(Character.toUpperCase(c));
            else break;
        }
        return sb.toString();
    }

    public static int estimateTokens(List<Message> messages) {
        int n = 0;
        for (Message m : messages) n += estimateTokens(m.content());
        return Math.max(1, n);
    }

    public static int estimateTokens(String s) {
        if (s == null || s.isEmpty()) return 0;
        // rough: CJK ~1 token/char, latin ~0.25
        int cjk = 0, other = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) cjk++;
            else other++;
        }
        return cjk + Math.max(1, other / 4);
    }

    private static String truncate(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n) + "...";
    }
}
