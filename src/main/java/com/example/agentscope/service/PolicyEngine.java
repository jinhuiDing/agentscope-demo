package com.example.agentscope.service;

import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PolicyEngine {

    private static final List<String> DENIED_PATHS = List.of(".git", ".env", ".env.", "target", ".agentscope/state");
    private static final List<String> ALLOWED_PATH_PREFIXES = List.of("src/", "README.md", "AGENTS.md", "agent-policy.yml", "agents.yml", "pom.xml");
    private static final List<String> ALLOWED_COMMANDS = List.of(
            "mvn -q -DskipTests package",
            "node --check src/main/resources/static/app.js",
            "git status --short",
            "git diff");
    private static final List<String> ASK_COMMANDS = List.of("git commit", "git checkout", "git switch", "mvn test");
    private static final List<String> DENIED_COMMANDS = List.of("rm", "del", "remove-item", "git reset", "git clean", "curl", "wget");

    public PolicyDecision checkRead(Path workspaceRoot, Path requestedPath) {
        return checkPath(workspaceRoot, requestedPath, false);
    }

    public PolicyDecision checkWrite(Path workspaceRoot, Path requestedPath) {
        return checkPath(workspaceRoot, requestedPath, true);
    }

    public PolicyDecision checkCommand(String command) {
        String normalized = normalizeCommand(command);
        if (!StringUtils.hasText(normalized)) {
            return PolicyDecision.deny("命令不能为空。");
        }
        for (String denied : DENIED_COMMANDS) {
            if (normalized.toLowerCase().startsWith(denied)) {
                return PolicyDecision.deny("命令被策略拒绝：" + denied);
            }
        }
        for (String allowed : ALLOWED_COMMANDS) {
            if (normalized.equalsIgnoreCase(allowed) || normalized.toLowerCase().startsWith(allowed.toLowerCase() + " ")) {
                return PolicyDecision.allow("命中允许命令规则。");
            }
        }
        for (String ask : ASK_COMMANDS) {
            if (normalized.toLowerCase().startsWith(ask.toLowerCase())) {
                return PolicyDecision.ask("命令需要人工确认：" + ask);
            }
        }
        return PolicyDecision.ask("未知命令需要人工确认。");
    }

    private PolicyDecision checkPath(Path workspaceRoot, Path requestedPath, boolean write) {
        Path root = workspaceRoot.toAbsolutePath().normalize();
        Path target = requestedPath.toAbsolutePath().normalize();
        if (!target.startsWith(root)) {
            return PolicyDecision.deny("路径超出项目工作区。");
        }
        String relative = root.relativize(target).toString().replace('\\', '/');
        for (String denied : DENIED_PATHS) {
            if (relative.equals(denied) || relative.startsWith(denied + "/") || relative.startsWith(denied)) {
                return PolicyDecision.deny("路径被策略拒绝：" + relative);
            }
        }
        if (!write) {
            return PolicyDecision.allow("读取允许。");
        }
        for (String allowed : ALLOWED_PATH_PREFIXES) {
            if (relative.equals(allowed) || relative.startsWith(allowed.endsWith("/") ? allowed : allowed + "/")) {
                return PolicyDecision.allow("命中允许写入路径规则。");
            }
        }
        return PolicyDecision.ask("写入路径需要人工确认：" + relative);
    }

    private String normalizeCommand(String command) {
        return command == null ? "" : command.trim().replace('\\', '/').replaceAll("\\s+", " ");
    }
}
