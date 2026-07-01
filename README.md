# AgentScope 开发团队

这是一个本地可用的 AgentScope + Spring Boot 多 Agent 项目。主 Agent 担任项目经理，按需调度需求分析、架构设计、编码、测试和代码审查子 Agent，帮助你完成从需求到验证的开发流程。

## 能力

- 浏览器聊天工作台，支持流式输出。
- `userId + sessionId` 维持会话上下文。
- 5 个真实 AgentScope 子 Agent 共享工作区协作。
- 开发团队可以在当前项目工作区内修改代码，并运行 Maven 检查。
- 支持复杂需求模式：自动或手动按复杂任务拆分里程碑、风险、验收标准和测试策略。
- 支持人工介入策略：关键点确认、严格确认或自动执行。
- 支持可观测性：前端时间线展示阶段、工具、人工确认、模型 token 和耗时；后端提供最近 trace 查询接口。
- 支持持久化任务记录：H2 保存 runs、events、approvals。
- 支持仓库工作台：查看文件树、`git status --short`、`git diff`。
- 支持后端权限策略：`agent-policy.yml` 约束路径、命令、审批和拒绝规则。
- 支持项目级代理规则：启动时读取 `AGENTS.md` 并注入主 Agent 提示。
- 运行时工作区默认位于 `.agentscope/workspace`。

## 团队角色

- `requirements-analyst`：澄清目标、拆分需求、定义验收标准。
- `architect`：设计实现方案、接口、数据流和风险。
- `developer`：修改代码，保持改动小、清晰、可运行。
- `tester`：运行或补充检查，验证构建、接口和回归场景。
- `reviewer`：审查缺陷、遗漏测试、兼容性和维护风险。

## 运行要求

- JDK 17 或更高版本
- Maven 3.9+
- DashScope API Key

当前项目依赖 Spring Boot 3.x 和 AgentScope 2.x，不能用 JDK 8 编译运行。

## 启动

PowerShell:

```powershell
$env:DASHSCOPE_API_KEY="sk-your-dashscope-key"
$env:AGENT_MODEL="qwen-plus"
mvn spring-boot:run
```

如果 PATH 里还是 JDK 8，但 IntelliJ 已安装，可以用脚本自动寻找 IntelliJ 自带的 JBR：

```powershell
$env:DASHSCOPE_API_KEY="sk-your-dashscope-key"
.\scripts\run.ps1 -Port 8081
```

打开浏览器：

```text
http://localhost:8080
```

如果使用 `-Port 8081`，则打开：

```text
http://localhost:8081
```

## 如何提交开发任务

可以直接描述目标：

```text
帮我给 README 增加一段接口使用说明，并跑一次构建检查。
```

也可以限制行为：

```text
先只做需求分析和实现方案，不要改代码。
```

复杂任务默认会按“需求分析 -> 架构方案 -> 编码 -> 测试 -> 审查 -> 总结”的流程协作；简单任务会跳过不必要角色。

工作台左侧可以选择：

- `完整研发`：适合从需求到代码和验证的完整流程。
- `只分析方案`：只做需求分析和设计，不改文件。
- `直接实现`：适合目标明确的小功能或修复。
- `只做审查`：适合检查已有改动。

人工介入策略：

- `关键点确认`：需求不清、影响多文件、测试失败或高风险时停下等待确认。
- `严格确认`：改文件、运行命令、采用关键方案前都先等待确认。
- `自动执行`：允许直接执行非破坏性操作，高风险操作仍需确认。

## HTTP API

获取 Agent 和团队信息：

```http
GET /api/agent
```

响应包含：

```json
{
  "name": "dev-team-agent",
  "model": "qwen-plus",
  "workspace": ".agentscope/workspace",
  "roles": [
    {"id": "developer", "name": "编码工程师", "description": "按方案修改代码，保持改动小、清晰、可运行。"}
  ]
}
```

流式聊天：

```http
POST /api/chat/stream
Content-Type: application/json

{
  "message": "帮我实现一个小功能并验证构建",
  "sessionId": "demo-session",
  "userId": "alice",
  "workflowMode": "full-cycle",
  "interventionMode": "checkpoint",
  "complexity": "auto"
}
```

响应是 Server-Sent Events，事件数据形如：

```json
{"type":"delta","content":"好的，我先让需求分析师拆解目标。"}
```

SSE 事件类型：

- `delta`：最终回复文本片段。
- `thinking`：模型思考片段。
- `tool`：工具或子 Agent 调用。
- `approval`：需要人工确认。
- `lifecycle`：阶段变化。
- `metric`：模型或工具指标。
- `done`：本轮完成。
- `error`：本轮错误。

查询最近运行观测记录：

```http
GET /api/runs/recent?limit=80
```

任务式 API：

```http
POST /api/runs
GET /api/runs/{runId}
GET /api/runs/{runId}/events
POST /api/runs/{runId}/cancel
GET /api/approvals/pending
POST /api/approvals/{approvalId}/approve
POST /api/approvals/{approvalId}/reject
GET /api/repo/tree
GET /api/repo/status
GET /api/repo/diff
```

说明：`POST /api/runs` 会创建持久化任务记录；实时模型执行仍使用 `/api/chat/stream`。审批 API 会记录批准/拒绝结果，第一版不会自动恢复已经暂停的 AgentScope 工具调用。

## 配置

可用环境变量：

- `DASHSCOPE_API_KEY` 或 `AGENT_API_KEY`：模型调用密钥。
- `AGENT_MODEL`：DashScope 模型名，默认 `qwen-plus`。
- `AGENT_NAME`：页面和接口展示的 Agent 名称，默认 `dev-team-agent`。
- `AGENT_WORKSPACE`：AgentScope 工作区目录，默认 `.agentscope/workspace`。
- `AGENT_MAX_ITERS`：单轮最大推理/工具迭代次数。
- `PORT`：服务端口，默认 `8080`。

## 安全边界

这个项目面向本地单用户开发。系统提示会约束团队只在当前项目工作区内读写文件，不执行破坏性命令，不修改无关文件。它不是多用户平台，也没有额外权限弹窗、数据库或任务队列。

强制策略由后端 `PolicyEngine` 执行，默认规则来自代码中的安全默认值，并在仓库中提供 `agent-policy.yml` 作为可读配置说明。当前强制覆盖：

- 拒绝 `.git/**`、`.env*`、`target/**`、`.agentscope/state/**`。
- 允许 `mvn -q -DskipTests package`、`node --check src/main/resources/static/app.js`、`git status --short`、`git diff`。
- 其他命令默认进入人工确认或拒绝。

第一版限制：

- 没有远程沙箱和 PR 创建。
- MCP、hooks、eval/replay 已预留配置方向，但尚未接入外部服务。
- 审批可以记录和展示，AgentScope 原生工具调用的暂停后恢复需要后续接 AgentScope 恢复机制。

## 常用验证

```powershell
$env:JAVA_HOME="C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.1.1\jbr"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn -q -DskipTests package
```
