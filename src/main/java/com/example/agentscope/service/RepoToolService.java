package com.example.agentscope.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

@Service
public class RepoToolService {

    private final Path workspaceRoot = Path.of("").toAbsolutePath().normalize();
    private final PolicyEngine policyEngine;

    public RepoToolService(PolicyEngine policyEngine) {
        this.policyEngine = policyEngine;
    }

    public Path workspaceRoot() {
        return workspaceRoot;
    }

    public List<String> tree(int maxFiles) {
        List<String> files = new ArrayList<>();
        try (var stream = Files.walk(workspaceRoot, 8)) {
            stream.filter(Files::isRegularFile)
                    .map(workspaceRoot::relativize)
                    .map(path -> path.toString().replace('\\', '/'))
                    .filter(path -> !path.startsWith(".git/"))
                    .filter(path -> !path.startsWith("target/"))
                    .filter(path -> !path.startsWith(".agentscope/state/"))
                    .limit(Math.max(1, Math.min(maxFiles, 500)))
                    .forEach(files::add);
        } catch (Exception ex) {
            files.add("ERROR: " + ex.getMessage());
        }
        return files;
    }

    public ToolExecutionResult readFile(String relativePath) {
        Path path = workspaceRoot.resolve(relativePath).normalize();
        PolicyDecision decision = policyEngine.checkRead(workspaceRoot, path);
        if (!decision.allowed()) {
            return ToolExecutionResult.blocked(decision);
        }
        try {
            return ToolExecutionResult.success(Files.readString(path, StandardCharsets.UTF_8));
        } catch (Exception ex) {
            return ToolExecutionResult.error(ex.getMessage());
        }
    }

    public ToolExecutionResult runCommand(String command) {
        PolicyDecision decision = policyEngine.checkCommand(command);
        if (!decision.allowed()) {
            return ToolExecutionResult.blocked(decision);
        }
        return execute(command, Duration.ofSeconds(60));
    }

    public ToolExecutionResult gitStatus() {
        return execute("git status --short", Duration.ofSeconds(20));
    }

    public ToolExecutionResult gitDiff() {
        return execute("git diff", Duration.ofSeconds(20));
    }

    private ToolExecutionResult execute(String command, Duration timeout) {
        try {
            ProcessBuilder builder = new ProcessBuilder("powershell", "-NoProfile", "-Command", command);
            builder.directory(workspaceRoot.toFile());
            builder.redirectErrorStream(true);
            Process process = builder.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append(System.lineSeparator());
                }
            }
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return ToolExecutionResult.error("命令超时。");
            }
            return new ToolExecutionResult(process.exitValue() == 0 ? "success" : "failed", output.toString(), null);
        } catch (Exception ex) {
            return ToolExecutionResult.error(ex.getMessage());
        }
    }
}
