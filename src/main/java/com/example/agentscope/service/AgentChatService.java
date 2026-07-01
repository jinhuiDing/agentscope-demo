package com.example.agentscope.service;

import com.example.agentscope.web.ChatRequest;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.UserMessage;
import io.agentscope.harness.agent.HarnessAgent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

@Service
public class AgentChatService {

    private final HarnessAgent agent;

    public AgentChatService(HarnessAgent agent) {
        this.agent = agent;
    }

    public Flux<AgentEvent> stream(ChatRequest request) {
        validate(request);
        RuntimeContext context = RuntimeContext.builder()
                .sessionId(normalize(request.getSessionId(), "default-session"))
                .userId(normalize(request.getUserId(), "web-user"))
                .build();
        return agent.streamEvents(new UserMessage(buildTeamRequest(request)), context);
    }

    private String buildTeamRequest(ChatRequest request) {
        return """
                【开发团队任务控制】
                工作流模式：%s
                复杂度策略：%s
                人工介入策略：%s

                执行要求：
                %s

                本地 Codex/Claude Lite 能力：
                - 项目规则来自 AGENTS.md，权限策略来自 agent-policy.yml。
                - 后端提供 run、approval、repo status、repo diff、repo tree 等 API 作为强制治理边界。
                - 写文件、命令执行和敏感路径访问必须遵守策略；遇到拒绝或需要审批时，先等待用户确认。
                - 不能绕过权限策略读取 .env、.git、target 或 .agentscope/state。

                用户原始任务：
                %s
                """.formatted(
                workflowInstruction(request.getWorkflowMode()),
                complexityInstruction(request.getComplexity()),
                interventionInstruction(request.getInterventionMode()),
                outputContract(),
                request.getMessage().trim());
    }

    private String workflowInstruction(String workflowMode) {
        return switch (normalize(workflowMode, "full-cycle")) {
            case "analysis-only" -> "只做需求分析和方案设计，不修改文件，不运行命令。";
            case "implementation" -> "以落地实现为目标；必要时先简短分析，再调度编码、测试和审查。";
            case "review-only" -> "只做代码审查和风险分析，不修改文件。";
            default -> "完整研发流程：需求分析 -> 架构方案 -> 编码 -> 测试 -> 审查 -> 总结。";
        };
    }

    private String complexityInstruction(String complexity) {
        return switch (normalize(complexity, "auto")) {
            case "high" -> "按复杂需求处理：先拆里程碑、验收标准、风险和回滚点，再执行。";
            case "low" -> "按简单任务处理：跳过不必要角色，但仍需给出验证结果。";
            default -> "自动判断复杂度；涉及多文件、接口、状态、权限、测试或不确定需求时按复杂需求处理。";
        };
    }

    private String interventionInstruction(String interventionMode) {
        return switch (normalize(interventionMode, "checkpoint")) {
            case "strict" -> "强人工介入：改文件、运行命令、采用关键方案前，先输出【需要人工确认】和待确认事项，然后停止等待用户回复。";
            case "auto" -> "自动执行：在当前项目工作区内可直接改文件和运行非破坏性检查，遇到高风险或破坏性操作必须停下确认。";
            default -> "检查点介入：需求不清、影响多文件、需要删除/迁移/重命名、测试失败或存在高风险时，输出【需要人工确认】并等待用户回复。";
        };
    }

    private String outputContract() {
        return """
                - 对复杂任务，先给出短计划，再执行。
                - 如果需要人工确认，必须使用醒目的“【需要人工确认】”标题，并列出选项、风险和推荐选择。
                - 如果修改了代码，最终必须列出改动摘要、验证命令、验证结果和残余风险。
                - 如果未修改代码，最终必须明确说明未修改原因和下一步建议。
                """;
    }

    private void validate(ChatRequest request) {
        if (request == null || !StringUtils.hasText(request.getMessage())) {
            throw new IllegalArgumentException("message 不能为空。");
        }
    }

    private String normalize(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
