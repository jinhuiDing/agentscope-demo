package com.example.agentscope.web;

import java.time.Instant;
import java.util.Map;

public record ApprovalRecord(
        String id,
        String runId,
        String status,
        String action,
        String subject,
        String reason,
        Instant createdAt,
        Instant updatedAt,
        Map<String, Object> metadata
) {
}
