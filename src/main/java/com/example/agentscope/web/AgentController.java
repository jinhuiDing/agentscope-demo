package com.example.agentscope.web;

import com.example.agentscope.config.AgentProperties;
import com.example.agentscope.config.DevelopmentTeam;
import com.example.agentscope.service.AgentChatService;
import com.example.agentscope.service.RunTraceStore;
import io.agentscope.core.event.AgentEndEvent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.AgentStartEvent;
import io.agentscope.core.event.ExceedMaxItersEvent;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.event.RequireExternalExecutionEvent;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ToolCallEndEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ChatUsage;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
public class AgentController {

    private static final Map<String, String> TEAM_EVENT_LABELS = Map.of(
            "requirements-analyst", "需求分析中",
            "architect", "架构设计中",
            "developer", "编码实现中",
            "tester", "测试验证中",
            "reviewer", "代码审查中"
    );

    private final AgentChatService chatService;
    private final AgentProperties properties;
    private final RunTraceStore traceStore;

    public AgentController(AgentChatService chatService, AgentProperties properties, RunTraceStore traceStore) {
        this.chatService = chatService;
        this.properties = properties;
        this.traceStore = traceStore;
    }

    @GetMapping("/agent")
    public AgentInfoResponse agentInfo() {
        return new AgentInfoResponse(properties.getName(), properties.getModel(), properties.getWorkspace(), DevelopmentTeam.roles());
    }

    @GetMapping("/runs/recent")
    public RunTraceResponse recentRuns(@RequestParam(defaultValue = "80") int limit) {
        return new RunTraceResponse(traceStore.recent(limit));
    }

    @PostMapping(value = "/chat/stream", consumes = MediaType.APPLICATION_JSON_VALUE)
    public SseEmitter stream(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(0L);
        RunContext run = new RunContext("run-" + UUID.randomUUID(), request.getSessionId(), request.getUserId(), System.nanoTime());
        traceStore.createRun(run.id(), run.sessionId(), run.userId(), request.getMessage(), requestMetadata(request));

        emit(emitter, run, ChatStreamEvent.lifecycle("任务已进入开发团队队列", run.id(), "queued", run.elapsedMs(), requestMetadata(request)));

        chatService.stream(request)
                .subscribe(
                        event -> sendEvent(emitter, run, event),
                        error -> completeWithError(emitter, run, error),
                        () -> complete(emitter, run));
        return emitter;
    }

    private void sendEvent(SseEmitter emitter, RunContext run, AgentEvent event) {
        ChatStreamEvent streamEvent = toStreamEvent(run, event);
        if (streamEvent != null) {
            emit(emitter, run, streamEvent);
        }
    }

    private ChatStreamEvent toStreamEvent(RunContext run, AgentEvent event) {
        AgentEventType type = event.getType();
        if (type == AgentEventType.AGENT_START) {
            AgentStartEvent start = (AgentStartEvent) event;
            return ChatStreamEvent.lifecycle("Agent 启动：" + start.getName(), run.id(), roleStage(start.getName()), run.elapsedMs(),
                    Map.of("agentName", value(start.getName()), "role", value(start.getRole())));
        }
        if (type == AgentEventType.MODEL_CALL_START) {
            return ChatStreamEvent.lifecycle("模型调用开始", run.id(), "model", run.elapsedMs(), Map.of());
        }
        if (type == AgentEventType.MODEL_CALL_END) {
            ChatUsage usage = ((ModelCallEndEvent) event).getUsage();
            return ChatStreamEvent.metric("模型调用完成", run.id(), run.elapsedMs(), usageMetadata(usage));
        }
        if (type == AgentEventType.TEXT_BLOCK_DELTA) {
            return ChatStreamEvent.delta(((TextBlockDeltaEvent) event).getDelta(), run.id(), run.elapsedMs());
        }
        if (type == AgentEventType.THINKING_BLOCK_DELTA) {
            return ChatStreamEvent.thinking(((ThinkingBlockDeltaEvent) event).getDelta(), run.id(), run.elapsedMs());
        }
        if (type == AgentEventType.TOOL_CALL_START) {
            ToolCallStartEvent toolEvent = (ToolCallStartEvent) event;
            return ChatStreamEvent.tool(toolLabel(toolEvent.getToolCallName()), run.id(), run.elapsedMs(),
                    toolMetadata(toolEvent.getToolCallName(), toolEvent.getToolCallId()));
        }
        if (type == AgentEventType.TOOL_CALL_END) {
            ToolCallEndEvent toolEvent = (ToolCallEndEvent) event;
            return ChatStreamEvent.lifecycle("工具调用结束：" + toolEvent.getToolCallName(), run.id(), "tool", run.elapsedMs(),
                    toolMetadata(toolEvent.getToolCallName(), toolEvent.getToolCallId()));
        }
        if (type == AgentEventType.TOOL_RESULT_TEXT_DELTA) {
            ToolResultTextDeltaEvent resultEvent = (ToolResultTextDeltaEvent) event;
            return ChatStreamEvent.tool(resultEvent.getDelta(), run.id(), run.elapsedMs(),
                    toolMetadata(resultEvent.getToolCallName(), resultEvent.getToolCallId()));
        }
        if (type == AgentEventType.TOOL_RESULT_END) {
            ToolResultEndEvent resultEvent = (ToolResultEndEvent) event;
            return ChatStreamEvent.metric("工具结果完成：" + resultEvent.getState(), run.id(), run.elapsedMs(),
                    toolMetadata(resultEvent.getToolCallName(), resultEvent.getToolCallId()));
        }
        if (type == AgentEventType.REQUIRE_USER_CONFIRM) {
            RequireUserConfirmEvent confirmEvent = (RequireUserConfirmEvent) event;
            return createApprovalEvent(run, "user_confirm", confirmEvent.getToolCalls());
        }
        if (type == AgentEventType.REQUIRE_EXTERNAL_EXECUTION) {
            RequireExternalExecutionEvent externalEvent = (RequireExternalExecutionEvent) event;
            return createApprovalEvent(run, "external_execution", externalEvent.getToolCalls());
        }
        if (type == AgentEventType.EXCEED_MAX_ITERS) {
            ExceedMaxItersEvent maxEvent = (ExceedMaxItersEvent) event;
            return ChatStreamEvent.metric("达到最大迭代次数", run.id(), run.elapsedMs(),
                    Map.of("maxIters", maxEvent.getMaxIters(), "currentIter", maxEvent.getCurrentIter()));
        }
        if (event instanceof AgentEndEvent || type == AgentEventType.AGENT_END) {
            return ChatStreamEvent.done(run.id(), run.elapsedMs(), Map.of());
        }
        return null;
    }

    private ChatStreamEvent createApprovalEvent(RunContext run, String action, List<ToolUseBlock> toolCalls) {
        List<String> names = toolCallNames(toolCalls);
        ApprovalRecord approval = traceStore.createApproval(run.id(), action, String.join(", ", names), "AgentScope 请求人工介入。", Map.of("toolCalls", names));
        return ChatStreamEvent.approval("需要人工确认：" + String.join(", ", names), run.id(), run.elapsedMs(),
                Map.of("approvalId", approval.id(), "toolCalls", names));
    }

    private void emit(SseEmitter emitter, RunContext run, ChatStreamEvent streamEvent) {
        try {
            traceStore.add(streamEvent.getRunId(), run.sessionId(), run.userId(), streamEvent.getType(), streamEvent.getStage(),
                    streamEvent.getContent(), streamEvent.getElapsedMs(), streamEvent.getMetadata());
            emitter.send(SseEmitter.event().name(streamEvent.getType()).data(streamEvent, MediaType.APPLICATION_JSON));
        } catch (IOException ex) {
            emitter.completeWithError(ex);
        }
    }

    private String toolLabel(String toolCallName) {
        if (!StringUtils.hasText(toolCallName)) {
            return "调用工具";
        }
        String normalized = toolCallName.trim();
        String label = TEAM_EVENT_LABELS.get(normalized);
        if (label != null) {
            return label + "：" + normalized;
        }
        for (Map.Entry<String, String> entry : TEAM_EVENT_LABELS.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                return entry.getValue() + "：" + normalized;
            }
        }
        return "调用工具：" + normalized;
    }

    private String roleStage(String agentName) {
        if (!StringUtils.hasText(agentName)) {
            return "agent";
        }
        for (String role : TEAM_EVENT_LABELS.keySet()) {
            if (agentName.contains(role)) {
                return role;
            }
        }
        return "agent";
    }

    private Map<String, Object> requestMetadata(ChatRequest request) {
        return Map.of(
                "workflowMode", value(request.getWorkflowMode()),
                "interventionMode", value(request.getInterventionMode()),
                "complexity", value(request.getComplexity()));
    }

    private Map<String, Object> usageMetadata(ChatUsage usage) {
        if (usage == null) {
            return Map.of();
        }
        return Map.of("inputTokens", usage.getInputTokens(), "outputTokens", usage.getOutputTokens(), "totalTokens", usage.getTotalTokens(), "modelTime", usage.getTime());
    }

    private Map<String, Object> toolMetadata(String toolName, String toolCallId) {
        return Map.of("toolName", value(toolName), "toolCallId", value(toolCallId));
    }

    private List<String> toolCallNames(List<ToolUseBlock> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return List.of("unknown");
        }
        return toolCalls.stream().map(ToolUseBlock::getName).map(this::value).toList();
    }

    private String value(String text) {
        return StringUtils.hasText(text) ? text : "";
    }

    private void completeWithError(SseEmitter emitter, RunContext run, Throwable error) {
        String message = StringUtils.hasText(error.getMessage()) ? error.getMessage() : error.getClass().getSimpleName();
        emit(emitter, run, ChatStreamEvent.error(message, run.id(), run.elapsedMs()));
        emitter.complete();
    }

    private void complete(SseEmitter emitter, RunContext run) {
        emit(emitter, run, ChatStreamEvent.done(run.id(), run.elapsedMs(), Map.of("status", "completed")));
        emitter.complete();
    }

    private record RunContext(String id, String sessionId, String userId, long startedAtNanos) {
        long elapsedMs() {
            return (System.nanoTime() - startedAtNanos) / 1_000_000;
        }
    }
}
