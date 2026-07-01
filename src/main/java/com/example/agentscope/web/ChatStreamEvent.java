package com.example.agentscope.web;

import java.time.Instant;
import java.util.Map;

public class ChatStreamEvent {

    private final String type;
    private final String content;
    private final String runId;
    private final String stage;
    private final long elapsedMs;
    private final String createdAt;
    private final Map<String, Object> metadata;

    private ChatStreamEvent(String type, String content, String runId, String stage, long elapsedMs, Map<String, Object> metadata) {
        this.type = type;
        this.content = content;
        this.runId = runId;
        this.stage = stage;
        this.elapsedMs = elapsedMs;
        this.createdAt = Instant.now().toString();
        this.metadata = metadata == null ? Map.of() : metadata;
    }

    public static ChatStreamEvent delta(String content, String runId, long elapsedMs) {
        return new ChatStreamEvent("delta", content, runId, "response", elapsedMs, Map.of());
    }

    public static ChatStreamEvent thinking(String content, String runId, long elapsedMs) {
        return new ChatStreamEvent("thinking", content, runId, "thinking", elapsedMs, Map.of());
    }

    public static ChatStreamEvent tool(String content, String runId, long elapsedMs, Map<String, Object> metadata) {
        return new ChatStreamEvent("tool", content, runId, "tool", elapsedMs, metadata);
    }

    public static ChatStreamEvent lifecycle(String content, String runId, String stage, long elapsedMs, Map<String, Object> metadata) {
        return new ChatStreamEvent("lifecycle", content, runId, stage, elapsedMs, metadata);
    }

    public static ChatStreamEvent approval(String content, String runId, long elapsedMs, Map<String, Object> metadata) {
        return new ChatStreamEvent("approval", content, runId, "human-review", elapsedMs, metadata);
    }

    public static ChatStreamEvent metric(String content, String runId, long elapsedMs, Map<String, Object> metadata) {
        return new ChatStreamEvent("metric", content, runId, "metrics", elapsedMs, metadata);
    }

    public static ChatStreamEvent done(String runId, long elapsedMs, Map<String, Object> metadata) {
        return new ChatStreamEvent("done", "", runId, "done", elapsedMs, metadata);
    }

    public static ChatStreamEvent error(String content, String runId, long elapsedMs) {
        return new ChatStreamEvent("error", content, runId, "error", elapsedMs, Map.of());
    }

    public String getType() {
        return type;
    }

    public String getContent() {
        return content;
    }

    public String getRunId() {
        return runId;
    }

    public String getStage() {
        return stage;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }
}
