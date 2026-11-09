package com.jianwen.springailab.structured;

/**
 * Tiny JSON → RefundDecision parser (no Jackson).
 * Production Spring AI uses BeanOutputConverter / JsonSchema; here we keep smoke offline.
 */
public final class StructuredOutputParser {

    public RefundDecision parseRefundDecision(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("empty structured output");
        }
        String s = json.trim();
        // strip markdown fences if model wraps
        if (s.startsWith("```")) {
            int nl = s.indexOf('\n');
            int end = s.lastIndexOf("```");
            if (nl > 0 && end > nl) s = s.substring(nl + 1, end).trim();
        }
        boolean approve = boolField(s, "approve");
        String reason = strField(s, "reason");
        int risk = intField(s, "riskScore");
        return new RefundDecision(approve, reason, risk);
    }

    private static boolean boolField(String json, String field) {
        String v = rawField(json, field);
        return "true".equalsIgnoreCase(v);
    }

    private static int intField(String json, String field) {
        String v = rawField(json, field);
        try { return Integer.parseInt(v.trim()); }
        catch (Exception e) { return 0; }
    }

    private static String strField(String json, String field) {
        String needle = "\"" + field + "\"";
        int i = json.indexOf(needle);
        if (i < 0) return "";
        int colon = json.indexOf(':', i + needle.length());
        int q1 = json.indexOf('"', colon + 1);
        int q2 = json.indexOf('"', q1 + 1);
        if (q1 < 0 || q2 < 0) return "";
        return json.substring(q1 + 1, q2);
    }

    private static String rawField(String json, String field) {
        String needle = "\"" + field + "\"";
        int i = json.indexOf(needle);
        if (i < 0) return "";
        int colon = json.indexOf(':', i + needle.length());
        if (colon < 0) return "";
        int j = colon + 1;
        while (j < json.length() && Character.isWhitespace(json.charAt(j))) j++;
        if (j < json.length() && json.charAt(j) == '"') {
            int q2 = json.indexOf('"', j + 1);
            return q2 < 0 ? "" : json.substring(j + 1, q2);
        }
        StringBuilder sb = new StringBuilder();
        while (j < json.length()) {
            char c = json.charAt(j);
            if (c == ',' || c == '}' || Character.isWhitespace(c)) break;
            sb.append(c);
            j++;
        }
        return sb.toString();
    }
}
