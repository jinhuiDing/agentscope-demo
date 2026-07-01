package com.example.agentscope.web;

public class ApprovalDecisionRequest {

    private String reason = "";
    private boolean rememberRule;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public boolean isRememberRule() {
        return rememberRule;
    }

    public void setRememberRule(boolean rememberRule) {
        this.rememberRule = rememberRule;
    }
}
