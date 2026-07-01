package com.example.agentscope.web;

public class ChatRequest {

    private String message;
    private String sessionId = "default-session";
    private String userId = "web-user";
    private String workflowMode = "full-cycle";
    private String interventionMode = "checkpoint";
    private String complexity = "auto";

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getWorkflowMode() {
        return workflowMode;
    }

    public void setWorkflowMode(String workflowMode) {
        this.workflowMode = workflowMode;
    }

    public String getInterventionMode() {
        return interventionMode;
    }

    public void setInterventionMode(String interventionMode) {
        this.interventionMode = interventionMode;
    }

    public String getComplexity() {
        return complexity;
    }

    public void setComplexity(String complexity) {
        this.complexity = complexity;
    }
}
