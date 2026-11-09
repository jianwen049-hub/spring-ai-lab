package com.jianwen.springailab.tool;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simulates a refund side-effect. Counts invocations so smoke can prove
 * IdempotentToolGuard prevents double execution.
 */
public final class RefundTool implements ToolCallback {
    private final AtomicInteger invocations = new AtomicInteger();

    @Override
    public String name() { return "refund_order"; }

    @Override
    public String description() {
        return "发起订单退款（支付域）。必须携带 orderId；金额单位分。";
    }

    @Override
    public String inputSchemaJson() {
        return "{\"type\":\"object\",\"required\":[\"orderId\",\"amountCents\"],"
                + "\"properties\":{\"orderId\":{\"type\":\"string\"},"
                + "\"amountCents\":{\"type\":\"integer\"},"
                + "\"reason\":{\"type\":\"string\"}}}";
    }

    @Override
    public String call(String argumentsJson) {
        int n = invocations.incrementAndGet();
        String orderId = IdempotentToolGuard.extract(argumentsJson, "orderId");
        return "{\"ok\":true,\"orderId\":\"" + orderId + "\",\"refundId\":\"RF-"
                + n + "-" + orderId + "\",\"sideEffectCount\":" + n + "}";
    }

    public int invocations() { return invocations.get(); }
}
