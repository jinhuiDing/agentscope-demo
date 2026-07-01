package com.example.agentscope.service;

public record ToolExecutionResult(String status, String output, PolicyDecision policy) {

    public static ToolExecutionResult success(String output) {
        return new ToolExecutionResult("success", output, null);
    }

    public static ToolExecutionResult blocked(PolicyDecision policy) {
        return new ToolExecutionResult(policy.status(), policy.reason(), policy);
    }

    public static ToolExecutionResult error(String output) {
        return new ToolExecutionResult("error", output, null);
    }
}
