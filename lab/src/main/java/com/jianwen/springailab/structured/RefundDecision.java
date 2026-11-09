package com.jianwen.springailab.structured;

/** Example structured output DTO for risk / refund decisions. */
public final class RefundDecision {
    public final boolean approve;
    public final String reason;
    public final int riskScore;

    public RefundDecision(boolean approve, String reason, int riskScore) {
        this.approve = approve;
        this.reason = reason;
        this.riskScore = riskScore;
    }

    @Override
    public String toString() {
        return "RefundDecision{approve=" + approve + ", reason='" + reason
                + "', riskScore=" + riskScore + "}";
    }
}
