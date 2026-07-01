const messages = document.querySelector("#messages");
const form = document.querySelector("#chat-form");
const input = document.querySelector("#message-input");
const sendButton = document.querySelector("#send-button");
const sessionInput = document.querySelector("#session-id");
const userInput = document.querySelector("#user-id");
const workflowMode = document.querySelector("#workflow-mode");
const interventionMode = document.querySelector("#intervention-mode");
const complexity = document.querySelector("#complexity");
const newSessionButton = document.querySelector("#new-session");
const agentName = document.querySelector("#agent-name");
const agentMeta = document.querySelector("#agent-meta");
const teamRoles = document.querySelector("#team-roles");
const timeline = document.querySelector("#timeline");
const approvalsPanel = document.querySelector("#approvals");
const repoOutput = document.querySelector("#repo-output");
const refreshWorkbench = document.querySelector("#refresh-workbench");
const metricTools = document.querySelector("#metric-tools");
const metricTokens = document.querySelector("#metric-tokens");
const metricElapsed = document.querySelector("#metric-elapsed");

const state = {
    runId: "",
    toolEvents: 0,
    totalTokens: 0,
    elapsedMs: 0
};

function createSessionId() {
    return `session-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}

function ensureSession() {
    const saved = localStorage.getItem("agentscope.sessionId");
    sessionInput.value = saved || createSessionId();
    localStorage.setItem("agentscope.sessionId", sessionInput.value);
}

function addMessage(role, content = "") {
    const item = document.createElement("div");
    item.className = `message ${role}`;
    item.textContent = content;
    messages.appendChild(item);
    messages.scrollTop = messages.scrollHeight;
    return item;
}

function appendText(element, text) {
    element.textContent += text || "";
    messages.scrollTop = messages.scrollHeight;
}

function setBusy(busy) {
    sendButton.disabled = busy;
    input.disabled = busy;
    sendButton.textContent = busy ? "协作中" : "发送";
}

function renderRoles(roles = []) {
    teamRoles.innerHTML = "";
    roles.forEach(role => {
        const item = document.createElement("div");
        item.className = "role-item";
        item.innerHTML = `<strong>${escapeHtml(role.name || role.id)}</strong><span>${escapeHtml(role.description || "")}</span>`;
        teamRoles.appendChild(item);
    });
}

async function loadAgentInfo() {
    try {
        const response = await fetch("/api/agent");
        const info = await response.json();
        agentName.textContent = info.name || "开发团队";
        agentMeta.textContent = `${info.model || "unknown model"} · ${info.workspace || "workspace"}`;
        renderRoles(info.roles || []);
    } catch (error) {
        agentMeta.textContent = "后端未就绪";
    }
}

async function sendMessage(message) {
    addMessage("user", message);
    const assistant = addMessage("assistant", "");
    resetRunView();
    setBusy(true);

    try {
        const response = await fetch("/api/chat/stream", {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({
                message,
                sessionId: sessionInput.value.trim() || createSessionId(),
                userId: userInput.value.trim() || "web-user",
                workflowMode: workflowMode.value,
                interventionMode: interventionMode.value,
                complexity: complexity.value
            })
        });

        if (!response.ok || !response.body) {
            throw new Error(`请求失败：${response.status}`);
        }

        await readSseStream(response.body, event => handleStreamEvent(event, assistant));
    } catch (error) {
        appendText(assistant, `\n\n${error.message || "请求失败，请检查后端日志。"}`);
    } finally {
        setBusy(false);
        input.focus();
        loadPendingApprovals();
    }
}

function handleStreamEvent(event, assistant) {
    if (event.runId) {
        state.runId = event.runId;
    }
    updateMetrics(event);
    if (event.type === "delta") {
        appendText(assistant, event.content);
    } else if (event.type === "thinking") {
        addTimeline(event, "思考", event.content);
    } else if (event.type === "tool") {
        addMessage("event", event.content);
        addTimeline(event, "工具", event.content);
    } else if (event.type === "approval") {
        addMessage("approval", event.content);
        addTimeline(event, "人工确认", event.content);
        loadPendingApprovals();
    } else if (event.type === "lifecycle") {
        addTimeline(event, "阶段", event.content);
    } else if (event.type === "metric") {
        addTimeline(event, "指标", event.content);
    } else if (event.type === "error") {
        addMessage("event", event.content || "服务端返回错误。");
        addTimeline(event, "错误", event.content);
    }
}

function updateMetrics(event) {
    state.elapsedMs = Math.max(state.elapsedMs, event.elapsedMs || 0);
    if (event.type === "tool" || event.type === "approval") {
        state.toolEvents += 1;
    }
    if (event.metadata && Number.isFinite(event.metadata.totalTokens)) {
        state.totalTokens += event.metadata.totalTokens;
    }
    metricTools.textContent = String(state.toolEvents);
    metricTokens.textContent = String(state.totalTokens);
    metricElapsed.textContent = `${state.elapsedMs} ms`;
}

function addTimeline(event, label, content) {
    if (!content) {
        return;
    }
    const item = document.createElement("div");
    item.className = `timeline-item ${event.type || ""}`;
    item.innerHTML = `<span>${event.elapsedMs || 0} ms</span><strong>${escapeHtml(label)}</strong><p>${escapeHtml(content)}</p>`;
    timeline.prepend(item);
}

function resetRunView() {
    state.runId = "";
    state.toolEvents = 0;
    state.totalTokens = 0;
    state.elapsedMs = 0;
    metricTools.textContent = "0";
    metricTokens.textContent = "0";
    metricElapsed.textContent = "0 ms";
    timeline.innerHTML = "";
}

async function loadRecentTraces() {
    try {
        const response = await fetch("/api/runs/recent?limit=30");
        const data = await response.json();
        timeline.innerHTML = "";
        (data.traces || []).reverse().forEach(trace => addTimeline(trace, trace.type, trace.content));
    } catch (error) {
        addTimeline({type: "error", elapsedMs: 0}, "错误", "无法读取最近运行记录。");
    }
}

async function loadPendingApprovals() {
    try {
        const response = await fetch("/api/approvals/pending?limit=20");
        const data = await response.json();
        renderApprovals(data.approvals || []);
    } catch (error) {
        approvalsPanel.textContent = "无法读取审批队列。";
    }
}

function renderApprovals(approvals) {
    approvalsPanel.innerHTML = "";
    if (!approvals.length) {
        approvalsPanel.textContent = "暂无待审批项。";
        return;
    }
    approvals.forEach(approval => {
        const item = document.createElement("div");
        item.className = "approval-card";
        item.innerHTML = `
            <strong>${escapeHtml(approval.action)}</strong>
            <p>${escapeHtml(approval.subject || "")}</p>
            <span>${escapeHtml(approval.reason || "")}</span>
            <div class="approval-actions">
                <button data-approval="${approval.id}" data-decision="approve" type="button">批准</button>
                <button data-approval="${approval.id}" data-decision="reject" type="button">拒绝</button>
            </div>
        `;
        approvalsPanel.appendChild(item);
    });
}

async function decideApproval(id, decision) {
    await fetch(`/api/approvals/${id}/${decision}`, {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({reason: decision === "approve" ? "用户批准" : "用户拒绝"})
    });
    loadPendingApprovals();
}

async function loadRepo(path) {
    repoOutput.textContent = "加载中...";
    try {
        const response = await fetch(path);
        const data = await response.json();
        if (Array.isArray(data)) {
            repoOutput.textContent = data.join("\n");
        } else {
            repoOutput.textContent = data.output || JSON.stringify(data, null, 2);
        }
    } catch (error) {
        repoOutput.textContent = error.message || "加载失败";
    }
}

async function readSseStream(body, onEvent) {
    const reader = body.getReader();
    const decoder = new TextDecoder("utf-8");
    let buffer = "";
    while (true) {
        const {done, value} = await reader.read();
        if (done) {
            break;
        }
        buffer += decoder.decode(value, {stream: true});
        const chunks = buffer.split("\n\n");
        buffer = chunks.pop() || "";
        chunks.forEach(chunk => parseSseChunk(chunk, onEvent));
    }
    if (buffer.trim()) {
        parseSseChunk(buffer, onEvent);
    }
}

function parseSseChunk(chunk, onEvent) {
    const dataLines = chunk.split("\n")
        .filter(line => line.startsWith("data:"))
        .map(line => line.slice(5).trimStart());
    if (!dataLines.length) {
        return;
    }
    try {
        onEvent(JSON.parse(dataLines.join("\n")));
    } catch (error) {
        onEvent({type: "error", content: "无法解析服务端事件。", elapsedMs: 0});
    }
}

function escapeHtml(value) {
    return String(value || "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

form.addEventListener("submit", event => {
    event.preventDefault();
    const message = input.value.trim();
    if (!message) {
        return;
    }
    input.value = "";
    sendMessage(message);
});

newSessionButton.addEventListener("click", () => {
    sessionInput.value = createSessionId();
    localStorage.setItem("agentscope.sessionId", sessionInput.value);
    messages.innerHTML = "";
    resetRunView();
    addWelcomeMessage();
    input.focus();
});

sessionInput.addEventListener("change", () => {
    localStorage.setItem("agentscope.sessionId", sessionInput.value.trim() || createSessionId());
});

document.querySelectorAll(".tab").forEach(tab => {
    tab.addEventListener("click", () => {
        document.querySelectorAll(".tab").forEach(item => item.classList.remove("active"));
        document.querySelectorAll(".tab-panel").forEach(item => item.classList.remove("active"));
        tab.classList.add("active");
        document.querySelector(`#${tab.dataset.tab === "repo" ? "repo-panel" : tab.dataset.tab}`).classList.add("active");
    });
});

approvalsPanel.addEventListener("click", event => {
    const target = event.target;
    if (!(target instanceof HTMLButtonElement)) {
        return;
    }
    const id = target.dataset.approval;
    const decision = target.dataset.decision;
    if (id && decision) {
        decideApproval(id, decision);
    }
});

refreshWorkbench.addEventListener("click", () => {
    loadRecentTraces();
    loadPendingApprovals();
});
document.querySelector("#load-status").addEventListener("click", () => loadRepo("/api/repo/status"));
document.querySelector("#load-diff").addEventListener("click", () => loadRepo("/api/repo/diff"));
document.querySelector("#load-tree").addEventListener("click", () => loadRepo("/api/repo/tree?limit=250"));

function addWelcomeMessage() {
    addMessage("assistant", "你好，我是本地开发代理工作台。现在具备任务记录、审批队列、仓库状态、diff、文件树、持久化 trace 和多 Agent 协作。");
}

ensureSession();
loadAgentInfo();
loadPendingApprovals();
addWelcomeMessage();
