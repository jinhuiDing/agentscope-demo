package com.example.agentscope.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

    private String name = "dev-team-agent";
    private String model = "qwen-plus";
    private String apiKey = "";
    private String workspace = ".agentscope/workspace";
    private int maxIters = 8;
    private int compactionTriggerMessages = 30;
    private int compactionKeepMessages = 10;
    private String systemPrompt = "";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getWorkspace() {
        return workspace;
    }

    public void setWorkspace(String workspace) {
        this.workspace = workspace;
    }

    public int getMaxIters() {
        return maxIters;
    }

    public void setMaxIters(int maxIters) {
        this.maxIters = maxIters;
    }

    public int getCompactionTriggerMessages() {
        return compactionTriggerMessages;
    }

    public void setCompactionTriggerMessages(int compactionTriggerMessages) {
        this.compactionTriggerMessages = compactionTriggerMessages;
    }

    public int getCompactionKeepMessages() {
        return compactionKeepMessages;
    }

    public void setCompactionKeepMessages(int compactionKeepMessages) {
        this.compactionKeepMessages = compactionKeepMessages;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }
}
