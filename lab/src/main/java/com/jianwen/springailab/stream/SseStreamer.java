package com.jianwen.springailab.stream;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Formats chat chunks as Server-Sent Events lines for a frontend EventSource.
 * Does not bind to servlet — keeps the lab framework-free.
 */
public final class SseStreamer {
    private final List<String> frames = new ArrayList<>();

    public Consumer<String> chunkSink() {
        return chunk -> frames.add(formatEvent("token", chunk));
    }

    public void complete(String finalJson) {
        frames.add(formatEvent("done", finalJson == null ? "{}" : finalJson));
        frames.add(""); // SSE stream end convention in demos
    }

    public void error(String message) {
        frames.add(formatEvent("error", message));
    }

    public List<String> frames() { return List.copyOf(frames); }

    public String asBody() {
        return String.join("\n", frames);
    }

    public static String formatEvent(String event, String data) {
        // multi-line data must be split per SSE spec; keep single-line for lab
        String safe = data.replace("\n", "\\n");
        return "event: " + event + "\ndata: " + safe + "\n";
    }
}
