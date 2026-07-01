package com.example.agentscope.web;

import java.time.Instant;
import java.util.Map;

public record RunRecord(
        String id,
        String sessionId,
        String userId,
        String status,
        String stage,
        String message,
        Instant createdAt,
        Instant updatedAt,
        Map<String, Object> metadata
) {
}
