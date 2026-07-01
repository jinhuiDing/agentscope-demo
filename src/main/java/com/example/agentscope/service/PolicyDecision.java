package com.example.agentscope.service;

public record PolicyDecision(String status, String reason) {

    public boolean allowed() {
        return "allow".equals(status);
    }

    public boolean asks() {
        return "ask".equals(status);
    }

    public static PolicyDecision allow(String reason) {
        return new PolicyDecision("allow", reason);
    }

    public static PolicyDecision ask(String reason) {
        return new PolicyDecision("ask", reason);
    }

    public static PolicyDecision deny(String reason) {
        return new PolicyDecision("deny", reason);
    }
}
