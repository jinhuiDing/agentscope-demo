package com.example.agentscope.web;

import java.time.Instant;
import java.util.Map;

public record TraceEntry(
        String runId,
        String sessionId,
        String userId,
        String type,
        String stage,
        String content,
        long elapsedMs,
        Instant createdAt,
        Map<String, Object> metadata
) {
}
