package com.example.agentscope.config;

import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties(AgentProperties.class)
public class AgentConfig {

    @Bean(destroyMethod = "close")
    public HarnessAgent harnessAgent(AgentProperties properties) {
        String apiKey = resolveApiKey(properties);
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("请先配置 DASHSCOPE_API_KEY 或 AGENT_API_KEY 环境变量。");
        }

        DashScopeChatModel model = DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName(properties.getModel())
                .stream(true)
                .build();

        String systemPrompt = StringUtils.hasText(properties.getSystemPrompt())
                ? properties.getSystemPrompt()
                : DevelopmentTeam.coordinatorPrompt(loadProjectGuidance());

        return HarnessAgent.builder()
                .name(properties.getName())
                .description("一个可以调度需求、架构、编码、测试和审查子 Agent 的本地开发团队。")
                .sysPrompt(systemPrompt)
                .model(model)
                .workspace(Paths.get(properties.getWorkspace()))
                .maxIters(properties.getMaxIters())
                .subagents(DevelopmentTeam.subagents(properties))
                .compaction(CompactionConfig.builder()
                        .triggerMessages(properties.getCompactionTriggerMessages())
                        .keepMessages(properties.getCompactionKeepMessages())
                        .build())
                .build();
    }

    private String resolveApiKey(AgentProperties properties) {
        if (StringUtils.hasText(properties.getApiKey())) {
            return properties.getApiKey();
        }
        String dashScopeKey = System.getenv("API_KEY");
        if (StringUtils.hasText(dashScopeKey)) {
            return dashScopeKey;
        }
        return System.getenv("AGENT_API_KEY");
    }

    private String loadProjectGuidance() {
        Path path = Paths.get("AGENTS.md");
        if (!Files.exists(path)) {
            return "";
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return "AGENTS.md 读取失败：" + ex.getMessage();
        }
    }
}
