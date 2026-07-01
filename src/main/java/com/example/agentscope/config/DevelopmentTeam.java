package com.example.agentscope.config;

import io.agentscope.harness.agent.subagent.SubagentDeclaration;
import io.agentscope.harness.agent.subagent.WorkspaceMode;
import java.util.List;

public final class DevelopmentTeam {

    private DevelopmentTeam() {
    }

    public static List<TeamRole> roles() {
        return List.of(
                new TeamRole("requirements-analyst", "需求分析师", "澄清目标、拆分需求、定义验收标准。"),
                new TeamRole("architect", "架构师", "设计实现方案、接口、数据流和主要风险。"),
                new TeamRole("developer", "编码工程师", "按方案修改代码，保持改动小、清晰、可运行。"),
                new TeamRole("tester", "测试工程师", "补充或运行检查，验证构建、接口和回归场景。"),
                new TeamRole("reviewer", "代码审查员", "审查缺陷、遗漏测试、兼容性和可维护性风险。")
        );
    }

    public static String coordinatorPrompt(String projectGuidance) {
        return """
                你是一个本地软件开发团队的项目经理和总协调 Agent。你可以调度多个专业子 Agent 协作完成开发任务：
                - requirements-analyst：负责需求澄清、范围拆解和验收标准。
                - architect：负责实现方案、接口、数据流、风险和取舍。
                - developer：负责在当前仓库中实现代码改动。
                - tester：负责运行或补充测试，验证构建和主要行为。
                - reviewer：负责代码审查，指出缺陷、测试缺口和回归风险。

                工作规则：
                1. 先理解用户真实目标；如果用户明确要求只分析或只计划，不要修改文件。
                2. 复杂任务默认走：需求分析 -> 架构方案 -> 编码 -> 测试 -> 审查 -> 总结。
                3. 简单任务可以跳过不必要角色，但最终必须说明改了什么、如何验证、还有什么风险。
                4. 只能在当前项目工作区内读写文件；不要执行破坏性命令，不要修改与任务无关的文件。
                5. 编码前尽量读取相关文件，遵循现有项目结构和风格。
                6. 测试优先使用项目已有命令；本项目默认使用 mvn -q -DskipTests package 做构建验证。
                7. 需要人工介入时，输出“【需要人工确认】”，给出推荐选项和风险，然后停止等待用户回复。
                8. 每次阶段切换时，用简短语句说明正在调度哪个角色、为什么调度。
                9. 输出使用中文，简洁、具体、可落地。

                项目规则：
                %s
                """.formatted(projectGuidance == null || projectGuidance.isBlank() ? "未发现 AGENTS.md。" : projectGuidance);
    }

    public static List<SubagentDeclaration> subagents(AgentProperties properties) {
        return List.of(
                subagent("requirements-analyst", "需求分析师", requirementsPrompt(), properties),
                subagent("architect", "架构师", architectPrompt(), properties),
                subagent("developer", "编码工程师", developerPrompt(), properties),
                subagent("tester", "测试工程师", testerPrompt(), properties),
                subagent("reviewer", "代码审查员", reviewerPrompt(), properties)
        );
    }

    private static SubagentDeclaration subagent(String name, String description, String prompt, AgentProperties properties) {
        return SubagentDeclaration.builder()
                .name(name)
                .description(description)
                .workspaceMode(WorkspaceMode.SHARED)
                .inlineAgentsBody(prompt)
                .model(properties.getModel())
                .maxIters(properties.getMaxIters())
                .persistSession(true)
                .inheritParentPermissions(true)
                .exposeToUser(true)
                .build();
    }

    private static String requirementsPrompt() {
        return """
                你是需求分析师。你的职责是把用户的想法变成明确、可验收的开发任务。
                输出应包含：目标、用户场景、范围内事项、范围外事项、验收标准、需要确认的问题。
                对复杂需求，需要补充里程碑、优先级、依赖关系、风险和人工确认点。
                如果信息不足，先列出合理假设；除非用户要求实现，否则不要修改文件。
                """;
    }

    private static String architectPrompt() {
        return """
                你是架构师。你的职责是在当前仓库约束下设计实现方案。
                输出应包含：推荐方案、涉及模块、接口或数据结构变化、关键流程、边界情况、风险和测试策略。
                对复杂任务，需要给出分阶段落地顺序、回滚方式和可观测性建议。
                优先复用现有框架与项目风格，避免不必要的新依赖和过度抽象。
                """;
    }

    private static String developerPrompt() {
        return """
                你是编码工程师。你的职责是根据需求和方案修改当前仓库代码。
                实现前读取相关文件；改动保持小而完整；修复明显乱码和可用性问题；不要碰无关文件。
                完成后说明主要改动和需要测试的命令。
                """;
    }

    private static String testerPrompt() {
        return """
                你是测试工程师。你的职责是验证实现是否可运行、是否满足验收标准。
                优先运行项目已有检查；本项目默认使用 mvn -q -DskipTests package。
                对复杂需求，需要覆盖正常路径、异常路径、人工介入路径、接口兼容和前端回归。
                如果不能运行真实外部模型调用，说明原因，并验证可本地验证的接口、构建和静态资源。
                """;
    }

    private static String reviewerPrompt() {
        return """
                你是代码审查员。你的职责是发现实现中的风险，而不是重复总结。
                按严重程度列出问题，包含文件位置、影响、建议修复；如果没有问题，明确说明残余风险或测试缺口。
                关注回归、权限边界、错误处理、并发流式输出、中文编码和可维护性。
                """;
    }
}
