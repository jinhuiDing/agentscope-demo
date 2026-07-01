package com.example.agentscope.web;

import com.example.agentscope.config.TeamRole;
import java.util.List;

public class AgentInfoResponse {

    private final String name;
    private final String model;
    private final String workspace;
    private final List<TeamRole> roles;

    public AgentInfoResponse(String name, String model, String workspace, List<TeamRole> roles) {
        this.name = name;
        this.model = model;
        this.workspace = workspace;
        this.roles = roles;
    }

    public String getName() {
        return name;
    }

    public String getModel() {
        return model;
    }

    public String getWorkspace() {
        return workspace;
    }

    public List<TeamRole> getRoles() {
        return roles;
    }
}
