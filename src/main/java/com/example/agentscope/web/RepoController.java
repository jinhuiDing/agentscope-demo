package com.example.agentscope.web;

import com.example.agentscope.service.RepoToolService;
import com.example.agentscope.service.ToolExecutionResult;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/repo")
public class RepoController {

    private final RepoToolService repoToolService;

    public RepoController(RepoToolService repoToolService) {
        this.repoToolService = repoToolService;
    }

    @GetMapping("/tree")
    public List<String> tree(@RequestParam(defaultValue = "200") int limit) {
        return repoToolService.tree(limit);
    }

    @GetMapping("/status")
    public ToolExecutionResult status() {
        return repoToolService.gitStatus();
    }

    @GetMapping("/diff")
    public ToolExecutionResult diff() {
        return repoToolService.gitDiff();
    }
}
