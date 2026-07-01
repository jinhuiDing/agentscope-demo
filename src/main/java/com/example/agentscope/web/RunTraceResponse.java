package com.example.agentscope.web;

import java.util.List;

public class RunTraceResponse {

    private final List<TraceEntry> traces;

    public RunTraceResponse(List<TraceEntry> traces) {
        this.traces = traces;
    }

    public List<TraceEntry> getTraces() {
        return traces;
    }
}
