package com.jianwen.springailab.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Offline toy "vector" store: bag-of-char / token overlap score.
 * Teaches the VectorStore contract without embedding APIs or FAISS.
 */
public final class InMemoryVectorStore implements VectorStore {
    private final List<Document> docs = new ArrayList<>();

    @Override
    public synchronized void add(List<Document> documents) {
        docs.addAll(documents);
    }

    @Override
    public synchronized List<Document> similaritySearch(String query, int topK) {
        Set<String> q = tokens(query);
        List<Scored> scored = new ArrayList<>();
        for (Document d : docs) {
            Set<String> t = tokens(d.text());
            double inter = 0;
            for (String x : q) if (t.contains(x)) inter++;
            double union = q.size() + t.size() - inter;
            double jaccard = union == 0 ? 0 : inter / union;
            scored.add(new Scored(d, jaccard));
        }
        scored.sort(Comparator.comparingDouble((Scored s) -> s.score).reversed());
        List<Document> out = new ArrayList<>();
        for (int i = 0; i < Math.min(topK, scored.size()); i++) {
            if (scored.get(i).score > 0) out.add(scored.get(i).doc);
        }
        return out;
    }

    private static Set<String> tokens(String s) {
        Set<String> out = new HashSet<>();
        if (s == null) return out;
        String lower = s.toLowerCase(Locale.ROOT);
        StringBuilder ascii = new StringBuilder();
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                out.add(String.valueOf(c));
                if (i + 1 < lower.length()
                        && Character.UnicodeScript.of(lower.charAt(i + 1)) == Character.UnicodeScript.HAN) {
                    out.add(lower.substring(i, i + 2));
                }
            } else if (Character.isLetterOrDigit(c)) {
                ascii.append(c);
            } else {
                if (ascii.length() > 0) {
                    out.add(ascii.toString());
                    ascii.setLength(0);
                }
            }
        }
        if (ascii.length() > 0) out.add(ascii.toString());
        return out;
    }

    private static final class Scored {
        final Document doc;
        final double score;
        Scored(Document doc, double score) { this.doc = doc; this.score = score; }
    }

    /** Seed payment / risk / ops style FAQ for demos. */
    public static InMemoryVectorStore demo() {
        InMemoryVectorStore vs = new InMemoryVectorStore();
        List<Document> seed = List.of(
                new Document("p1", "退款申请提交后，原路退回一般 T+1 到账；大额需风控复核。",
                        Map.of("domain", "payment")),
                new Document("p2", "订单号格式为 ORD-YYYY-序号，客服与支付对账均以此为准。",
                        Map.of("domain", "payment")),
                new Document("r1", "风控拒绝码 R-403 表示设备指纹异常，需用户重新核身。",
                        Map.of("domain", "risk")),
                new Document("o1", "夜间批处理窗口 02:00-04:00，此间退款查询可能延迟。",
                        Map.of("domain", "ops"))
        );
        vs.add(seed);
        return vs;
    }
}
