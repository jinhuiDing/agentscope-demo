package com.example.agentscope.web;

import com.example.agentscope.service.RunTraceStore;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RunController {

    private final RunTraceStore store;

    public RunController(RunTraceStore store) {
        this.store = store;
    }

    @PostMapping("/runs")
    public CreateRunResponse createRun(@RequestBody ChatRequest request) {
        String runId = "run-" + UUID.randomUUID();
        store.createRun(runId, request.getSessionId(), request.getUserId(), request.getMessage(), Map.of(
                "workflowMode", value(request.getWorkflowMode()),
                "interventionMode", value(request.getInterventionMode()),
                "complexity", value(request.getComplexity())));
        return new CreateRunResponse(runId, "queued");
    }

    @GetMapping("/runs/{runId}")
    public RunDetailResponse runDetail(@PathVariable String runId, @RequestParam(defaultValue = "300") int limit) {
        return new RunDetailResponse(
                store.findRun(runId).orElse(null),
                store.eventsForRun(runId, limit),
                store.approvalsForRun(runId));
    }

    @GetMapping("/runs/{runId}/events")
    public RunTraceResponse runEvents(@PathVariable String runId, @RequestParam(defaultValue = "300") int limit) {
        return new RunTraceResponse(store.eventsForRun(runId, limit));
    }

    @PostMapping("/runs/{runId}/cancel")
    public CreateRunResponse cancelRun(@PathVariable String runId) {
        store.updateRun(runId, "cancelled", "cancelled");
        return new CreateRunResponse(runId, "cancelled");
    }

    @GetMapping("/approvals/pending")
    public ApprovalListResponse pendingApprovals(@RequestParam(defaultValue = "50") int limit) {
        return new ApprovalListResponse(store.pendingApprovals(limit));
    }

    @PostMapping("/approvals/{approvalId}/approve")
    public ApprovalRecord approve(@PathVariable String approvalId, @RequestBody(required = false) ApprovalDecisionRequest request) {
        String reason = request == null ? "approved" : value(request.getReason());
        return store.decideApproval(approvalId, "approved", reason).orElse(null);
    }

    @PostMapping("/approvals/{approvalId}/reject")
    public ApprovalRecord reject(@PathVariable String approvalId, @RequestBody(required = false) ApprovalDecisionRequest request) {
        String reason = request == null ? "rejected" : value(request.getReason());
        return store.decideApproval(approvalId, "rejected", reason).orElse(null);
    }

    private String value(String text) {
        return text == null ? "" : text;
    }
}
