package com.example.agentscope.web;

import java.util.List;

public record RunDetailResponse(
        RunRecord run,
        List<TraceEntry> events,
        List<ApprovalRecord> approvals
) {
}
