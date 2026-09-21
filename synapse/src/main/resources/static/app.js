const state = {
  apiKey: localStorage.getItem('synapse-api-key') || '',
  workspaces: [],
  selectedWorkspaceId: null,
  snapshot: null,
  evaluation: null,
  report: '',
  explain: null
};

const els = {};
const moneyFmt = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });
const numberFmt = new Intl.NumberFormat('en-US');

document.addEventListener('DOMContentLoaded', () => {
  captureElements();
  bindEvents();
  els.apiKey.value = state.apiKey;
  boot();
});

function captureElements() {
  [
    'apiKey', 'saveApiKey', 'workspaceList', 'workspaceName', 'workspaceDescription',
    'statsGrid', 'board', 'boardSubtitle', 'boardMeta', 'agentRail', 'approvalsRail',
    'messageFeed', 'memoryFeed', 'memoryExplain', 'memoryMeta', 'contextMeta',
    'evaluationMeta', 'evaluationCards', 'criteriaList', 'reportPanel',
    'orgForm', 'roleForm', 'teamForm', 'goalForm',
    'seedDemo', 'runEvaluation', 'refreshAll', 'createOrgShortcut', 'loadDemoReport',
    'copyReport', 'toast'
  ].forEach((id) => {
    els[id] = document.getElementById(id);
  });
}

function bindEvents() {
  els.saveApiKey.addEventListener('click', () => {
    state.apiKey = els.apiKey.value.trim();
    localStorage.setItem('synapse-api-key', state.apiKey);
    notify('API key saved.');
  });

  els.refreshAll.addEventListener('click', () => boot(true));
  els.createOrgShortcut.addEventListener('click', () => els.orgForm.scrollIntoView({ behavior: 'smooth', block: 'center' }));
  els.seedDemo.addEventListener('click', seedDemoWorkspace);
  els.runEvaluation.addEventListener('click', runEvaluation);
  els.loadDemoReport.addEventListener('click', loadEvaluation);
  els.copyReport.addEventListener('click', copyReport);

  els.orgForm.addEventListener('submit', createOrganization);
  els.roleForm.addEventListener('submit', createRole);
  els.teamForm.addEventListener('submit', createTeam);
  els.goalForm.addEventListener('submit', createGoal);
}

async function boot(forceRefresh = false) {
  try {
    setBusy(true);
    await loadWorkspaces();
    if ((!state.selectedWorkspaceId || forceRefresh) && state.workspaces.length > 0) {
      state.selectedWorkspaceId = state.workspaces[0].organizationId;
    }
    if (state.selectedWorkspaceId) {
      await loadSnapshot(state.selectedWorkspaceId);
    } else {
      state.snapshot = null;
      render();
    }
    await loadEvaluation();
  } catch (error) {
    notify(error.message || 'Unable to load SYNAPSE.');
  } finally {
    setBusy(false);
  }
}

async function loadWorkspaces() {
  state.workspaces = await api('/workspaces');
  renderWorkspaceList();
}

async function loadSnapshot(id) {
  state.snapshot = await api(`/workspaces/${id}`);
  render();
}

async function loadEvaluation() {
  try {
    state.evaluation = await api('/demo/report');
    state.report = state.evaluation.reportMarkdown || '';
    renderEvaluation();
  } catch (error) {
    notify(error.message || 'Could not load evaluation report.');
  }
}

async function seedDemoWorkspace() {
  try {
    const response = await api('/demo/seed', { method: 'POST' });
    notify(`Seeded ${response.organizationName}.`);
    await boot(true);
    const demo = state.workspaces.find((item) => item.organizationName === 'AI Product Team');
    if (demo) {
      selectWorkspace(demo.organizationId);
    }
  } catch (error) {
    notify(error.message || 'Demo seeding failed.');
  }
}

async function runEvaluation() {
  try {
    state.evaluation = await api('/demo/evaluate?runs=50', { method: 'POST' });
    state.report = state.evaluation.reportMarkdown || '';
    renderEvaluation();
    notify('Evaluation complete.');
  } catch (error) {
    notify(error.message || 'Evaluation failed.');
  }
}

async function selectWorkspace(id) {
  state.selectedWorkspaceId = id;
  await loadSnapshot(id);
}

async function createOrganization(event) {
  event.preventDefault();
  const form = new FormData(event.target);
  const payload = {
    name: form.get('name').trim(),
    totalBudgetUsd: valueOrNull(form.get('budget'))
  };

  try {
    await api('/organizations', { method: 'POST', body: payload });
    event.target.reset();
    notify('Organization created.');
    await boot(true);
  } catch (error) {
    notify(error.message || 'Organization creation failed.');
  }
}

async function createRole(event) {
  event.preventDefault();
  if (!state.selectedWorkspaceId) {
    notify('Select a workspace first.');
    return;
  }
  const form = new FormData(event.target);
  const responsibilities = String(form.get('responsibilities') || '')
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean);
  const payload = {
    name: form.get('name').trim(),
    modelTier: form.get('tier'),
    tokenBudget: valueOrNull(form.get('tokenBudget')),
    systemPrompt: String(form.get('systemPrompt') || ''),
    responsibilities
  };

  try {
    await api(`/organizations/${state.selectedWorkspaceId}/roles`, { method: 'POST', body: payload });
    event.target.reset();
    notify('Role created.');
    await loadSnapshot(state.selectedWorkspaceId);
    await loadWorkspaces();
  } catch (error) {
    notify(error.message || 'Role creation failed.');
  }
}

async function createTeam(event) {
  event.preventDefault();
  if (!state.selectedWorkspaceId) {
    notify('Select a workspace first.');
    return;
  }
  const form = new FormData(event.target);
  const payload = {
    name: form.get('name').trim(),
    teamTopology: form.get('teamTopology')
  };

  try {
    await api(`/organizations/${state.selectedWorkspaceId}/teams`, { method: 'POST', body: payload });
    event.target.reset();
    notify('Team created.');
    await loadSnapshot(state.selectedWorkspaceId);
  } catch (error) {
    notify(error.message || 'Team creation failed.');
  }
}

async function createGoal(event) {
  event.preventDefault();
  if (!state.selectedWorkspaceId) {
    notify('Select a workspace first.');
    return;
  }
  const form = new FormData(event.target);
  const payload = { description: form.get('description').trim() };

  try {
    await api(`/organizations/${state.selectedWorkspaceId}/goals`, { method: 'POST', body: payload });
    event.target.reset();
    notify('Goal created and orchestrated.');
    await loadSnapshot(state.selectedWorkspaceId);
    await loadWorkspaces();
  } catch (error) {
    notify(error.message || 'Goal creation failed.');
  }
}

async function updateTask(taskId, status) {
  await api(`/workspaces/tasks/${taskId}`, {
    method: 'PATCH',
    body: { status }
  });
  if (state.selectedWorkspaceId) {
    await loadSnapshot(state.selectedWorkspaceId);
  }
}

async function resolveApproval(approvalId, decision) {
  await api(`/approvals/${approvalId}/resolve`, {
    method: 'POST',
    body: { decision, resolvedBy: 'SYNAPSE Studio' }
  });
  if (state.selectedWorkspaceId) {
    await loadSnapshot(state.selectedWorkspaceId);
  }
}

async function explainMemory(memoryId) {
  try {
    state.explain = await api(`/memory/decisions/${memoryId}/explain`);
    renderMemoryExplain();
  } catch (error) {
    notify(error.message || 'Could not explain memory.');
  }
}

function render() {
  renderWorkspaceList();
  renderHero();
  renderStats();
  renderBoard();
  renderAgents();
  renderApprovals();
  renderMessages();
  renderMemories();
  renderMemoryExplain();
  renderContextMeta();
  renderEvaluation();
}

function renderWorkspaceList() {
  els.workspaceList.innerHTML = state.workspaces.length === 0
    ? `<div class="muted">No workspaces yet. Seed the demo or create one.</div>`
    : state.workspaces.map((workspace) => {
        const active = workspace.organizationId === state.selectedWorkspaceId ? 'active' : '';
        return `
          <div class="workspace-card ${active}" data-workspace="${workspace.organizationId}">
            <h4>${escapeHtml(workspace.organizationName)}</h4>
            <p>${escapeHtml(workspace.budgetStatus || 'OK')} · ${workspace.taskCount || 0} tasks · ${workspace.agentCount || 0} agents</p>
            <div class="meta-row">
              <span class="chip">${moneyFmt.format(workspace.spentBudgetUsd || 0)} spent</span>
              <span class="chip">${moneyFmt.format(workspace.totalBudgetUsd || 0)} budget</span>
            </div>
          </div>
        `;
      }).join('');

  els.workspaceList.querySelectorAll('[data-workspace]').forEach((card) => {
    card.addEventListener('click', () => selectWorkspace(card.dataset.workspace));
  });
}

function renderHero() {
  if (!state.snapshot) {
    els.workspaceName.textContent = 'Select a workspace';
    els.workspaceDescription.textContent = 'Create an organization or seed the demo workspace to explore the full control room.';
    return;
  }

  const summary = state.snapshot.summary;
  els.workspaceName.textContent = summary.organizationName;
  els.workspaceDescription.textContent = `${summary.budgetStatus} budget health, ${summary.activeTaskCount} active tasks, and live agent context across the board.`;
}

function renderStats() {
  const summary = state.snapshot?.summary;
  const cards = summary ? [
    statCard('Budget', moneyFmt.format(summary.totalBudgetUsd || 0), `${moneyFmt.format(summary.spentBudgetUsd || 0)} spent · ${summary.budgetStatus}`),
    statCard('Agents', numberFmt.format(summary.agentCount || 0), `${summary.roleCount || 0} roles · ${summary.teamCount || 0} teams`),
    statCard('Goals', numberFmt.format(summary.goalCount || 0), `${summary.completedTaskCount || 0} completed tasks`),
    statCard('Tasks', numberFmt.format(summary.taskCount || 0), `${summary.activeTaskCount || 0} active · ${summary.blockedTaskCount || 0} blocked`)
  ] : [
    statCard('Budget', '—', 'Create or select a workspace'),
    statCard('Agents', '—', 'Waiting for context'),
    statCard('Goals', '—', 'Waiting for context'),
    statCard('Tasks', '—', 'Waiting for context')
  ];
  els.statsGrid.innerHTML = cards.join('');
}

function renderBoard() {
  const board = state.snapshot?.board;
  if (!board) {
    els.board.innerHTML = `<div class="muted">No board available.</div>`;
    els.boardMeta.textContent = '';
    return;
  }

  const lanes = board.lanes || {};
  const laneOrder = ['PENDING', 'ASSIGNED', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'BLOCKED'];
  els.board.innerHTML = laneOrder.map((lane) => {
    const tasks = lanes[lane] || [];
    const description = {
      PENDING: 'Backlog',
      ASSIGNED: 'Assigned',
      IN_PROGRESS: 'Working',
      COMPLETED: 'Done',
      FAILED: 'Failed',
      BLOCKED: 'Blocked'
    }[lane] || lane;
    return `
      <div class="lane" data-lane="${lane}">
        <div class="lane-header">
          <div>
            <div class="lane-title">${description}</div>
            <div class="muted">${lane} lane</div>
          </div>
          <div class="lane-pill">${tasks.length}</div>
        </div>
        <div class="task-list">
          ${tasks.map(renderTaskCard).join('')}
        </div>
      </div>
    `;
  }).join('');

  els.boardMeta.textContent = `${board.agents?.length || 0} agents loaded`;
  bindBoardDnD();
}

function renderAgents() {
  const agents = state.snapshot?.agents || [];
  els.agentRail.innerHTML = agents.length === 0
    ? `<div class="muted">No agents yet.</div>`
    : agents.map((agent) => `
      <div class="agent-card ${agent.status === 'WORKING' ? 'active' : ''}">
        <div class="meta-row">
          <span class="chip accent">${escapeHtml(agent.roleName || 'Agent')}</span>
          <span class="chip">${escapeHtml(agent.teamName || 'No team')}</span>
        </div>
        <h4>${escapeHtml(agent.roleName || 'Agent')}</h4>
        <p>${escapeHtml(agent.status || 'IDLE')} · ${agent.activeTaskCount || 0} active tasks · ${agent.tokensUsed || 0} tokens</p>
      </div>
    `).join('');
}

function renderApprovals() {
  const approvals = state.snapshot?.approvals || [];
  els.approvalsRail.innerHTML = approvals.length === 0
    ? `<div class="muted">No pending approvals.</div>`
    : approvals.map((approval) => `
      <div class="approval-card">
        <div class="meta-row">
          <span class="chip warn">${escapeHtml(approval.effectType || 'APPROVAL')}</span>
          <span class="chip">${escapeHtml(approval.status || 'PENDING')}</span>
        </div>
        <h4>${escapeHtml(approval.actionDescription || 'Approval required')}</h4>
        <p>Confidence ${approval.confidence ?? '—'} · Task ${approval.taskId || '—'}</p>
        <div class="meta-row" style="margin-top:12px">
          <button class="button button-secondary" data-approve="${approval.id}">Approve</button>
          <button class="button button-ghost" data-reject="${approval.id}">Reject</button>
        </div>
      </div>
    `).join('');

  els.approvalsRail.querySelectorAll('[data-approve]').forEach((button) => {
    button.addEventListener('click', () => resolveApproval(button.dataset.approve, 'APPROVED'));
  });
  els.approvalsRail.querySelectorAll('[data-reject]').forEach((button) => {
    button.addEventListener('click', () => resolveApproval(button.dataset.reject, 'REJECTED'));
  });
}

function renderMessages() {
  const messages = state.snapshot?.messages || [];
  els.messageFeed.innerHTML = messages.length === 0
    ? `<div class="muted">No messages yet. Run the demo or execute a goal.</div>`
    : messages.map((message) => `
      <div class="feed-item">
        <h4>${escapeHtml(message.content || 'Message')}</h4>
        <p>Run ${shortId(message.runId)} · Trace ${shortId(message.traceId)} · Confidence ${message.confidence ?? '—'}</p>
        <div class="meta-row">
          <span class="chip">${shortId(message.senderAgentId)}</span>
          ${message.recipientAgentId ? `<span class="chip">${shortId(message.recipientAgentId)}</span>` : ''}
        </div>
      </div>
    `).join('');
}

function renderMemories() {
  const memories = state.snapshot?.memories || [];
  els.memoryFeed.innerHTML = memories.length === 0
    ? `<div class="muted">No memory entries yet.</div>`
    : memories.map((memory) => `
      <div class="memory-card">
        <h4>${escapeHtml(memory.content || 'Memory')}</h4>
        <p>${escapeHtml(memory.scope || 'ORG')} · Confidence ${memory.confidence ?? '—'} · ${memory.superseded ? 'Superseded' : 'Active'}</p>
        <div class="meta-row">
          <span class="chip">${shortId(memory.id)}</span>
          <button class="button button-ghost" data-explain="${memory.id}">Explain</button>
        </div>
      </div>
    `).join('');

  els.memoryFeed.querySelectorAll('[data-explain]').forEach((button) => {
    button.addEventListener('click', () => explainMemory(button.dataset.explain));
  });
  els.memoryMeta.textContent = `${memories.length} memories loaded`;
}

function renderMemoryExplain() {
  if (!state.explain) {
    els.memoryExplain.classList.add('hidden');
    els.memoryExplain.innerHTML = '';
    return;
  }

  const decision = state.explain.decision;
  const evidence = state.explain.evidence || [];
  const alternatives = state.explain.alternatives || [];
  els.memoryExplain.classList.remove('hidden');
  els.memoryExplain.innerHTML = `
    <div class="panel-title">Explain: ${escapeHtml(decision.content || 'Decision')}</div>
    <div class="meta-row">
      <span class="chip good">${evidence.length} evidence</span>
      <span class="chip warn">${alternatives.length} alternatives</span>
    </div>
    <div style="margin-top:12px" class="stack">
      ${evidence.map((item) => `<div class="feed-item"><p>${escapeHtml(item.content)}</p></div>`).join('')}
      ${alternatives.map((item) => `<div class="feed-item"><p>${escapeHtml(item.content)}</p></div>`).join('')}
    </div>
  `;
}

function renderContextMeta() {
  const messages = state.snapshot?.messages || [];
  const memories = state.snapshot?.memories || [];
  els.contextMeta.textContent = `${messages.length} messages · ${memories.length} memories`;
}

function renderEvaluation() {
  const evaluation = state.evaluation;
  if (!evaluation) {
    els.evaluationMeta.textContent = 'Run evaluation to populate the benchmark report.';
    els.evaluationCards.innerHTML = '';
    els.criteriaList.innerHTML = '';
    els.reportPanel.textContent = '';
    return;
  }

  const bench = evaluation.benchmark || {};
  els.evaluationMeta.textContent = `${bench.runs || 0} repeated runs · ${bench.taskCount || 0} tasks/run`;
  els.evaluationCards.innerHTML = [
    statCard('Naive calls', numberFmt.format(bench.meanNaiveApiCalls || 0), `σ ${formatSmall(bench.stdDevNaiveApiCalls)}`),
    statCard('Batched calls', numberFmt.format(bench.meanBatchedApiCalls || 0), `σ ${formatSmall(bench.stdDevBatchedApiCalls)}`),
    statCard('Naive cost', moneyFmt.format(bench.meanNaiveCostUsd || 0), `σ ${formatSmall(bench.stdDevNaiveCostUsd)}`),
    statCard('Batched cost', moneyFmt.format(bench.meanBatchedCostUsd || 0), `σ ${formatSmall(bench.stdDevBatchedCostUsd)}`)
  ].join('');

  els.criteriaList.innerHTML = (evaluation.criteria || []).map((item) => `
    <div class="criterion">
      <div>
        <div style="font-weight:700">${escapeHtml(item.name)}</div>
        <div class="muted">${escapeHtml(item.details)}</div>
      </div>
      <div class="chip ${item.passed ? 'good' : 'bad'}">${item.passed ? 'PASS' : 'FAIL'}</div>
    </div>
  `).join('');

  els.reportPanel.textContent = state.report || '';
}

function renderTaskCard(task) {
  const statusChip = task.status === 'COMPLETED' ? 'good' : task.status === 'FAILED' || task.status === 'BLOCKED' ? 'bad' : 'warn';
  const assigned = task.assignedAgentName ? `<span class="chip accent">${escapeHtml(task.assignedAgentName)}</span>` : '';
  const skills = (task.requiredSkillTags || []).map((skill) => `<span class="chip">${escapeHtml(skill)}</span>`).join('');
  return `
    <div class="task-card" draggable="true" data-task-id="${task.id}">
      <div class="meta-row">
        <span class="chip ${statusChip}">${escapeHtml(task.status || 'PENDING')}</span>
        ${task.priority != null ? `<span class="chip">P${task.priority}</span>` : ''}
      </div>
      <div class="task-title">${escapeHtml(task.description || 'Task')}</div>
      <div class="chip-row">${assigned}${skills}</div>
      <div class="meta-row" style="margin-top:10px">
        ${task.estimatedCostUsd != null ? `<span class="chip">Est ${moneyFmt.format(task.estimatedCostUsd)}</span>` : ''}
        ${task.actualCostUsd != null ? `<span class="chip">Act ${moneyFmt.format(task.actualCostUsd)}</span>` : ''}
      </div>
    </div>
  `;
}

function bindBoardDnD() {
  const cards = document.querySelectorAll('.task-card');
  const lanes = document.querySelectorAll('.lane');

  cards.forEach((card) => {
    card.addEventListener('dragstart', (event) => {
      card.classList.add('dragging');
      event.dataTransfer.setData('text/plain', card.dataset.taskId);
    });
    card.addEventListener('dragend', () => card.classList.remove('dragging'));
  });

  lanes.forEach((lane) => {
    lane.addEventListener('dragover', (event) => event.preventDefault());
    lane.addEventListener('drop', async (event) => {
      event.preventDefault();
      const taskId = event.dataTransfer.getData('text/plain');
      const status = lane.dataset.lane;
      if (!taskId) {
        return;
      }
      try {
        await updateTask(taskId, status);
        notify(`Task moved to ${status}.`);
      } catch (error) {
        notify(error.message || 'Could not move task.');
      }
    });
  });
}

async function api(path, options = {}) {
  const headers = { 'Accept': 'application/json', ...(options.headers || {}) };
  if (state.apiKey) {
    headers['X-API-Key'] = state.apiKey;
  }
  const init = { ...options, headers };
  if (init.body && typeof init.body === 'object' && !(init.body instanceof FormData)) {
    headers['Content-Type'] = 'application/json';
    init.body = JSON.stringify(init.body);
  }

  const response = await fetch(path, init);
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || response.statusText);
  }
  if (response.status === 204) {
    return null;
  }
  return response.json();
}

function setBusy(busy) {
  document.body.style.cursor = busy ? 'progress' : 'default';
}

function notify(message) {
  els.toast.textContent = message;
  els.toast.classList.remove('hidden');
  clearTimeout(window.__toastTimer);
  window.__toastTimer = setTimeout(() => els.toast.classList.add('hidden'), 2800);
}

function statCard(label, value, foot) {
  return `
    <div class="stat-card">
      <div class="stat-label">${escapeHtml(label)}</div>
      <div class="stat-value">${escapeHtml(value)}</div>
      <div class="stat-foot">${escapeHtml(foot)}</div>
    </div>
  `;
}

function valueOrNull(value) {
  const text = String(value || '').trim();
  if (!text) {
    return null;
  }
  const numeric = Number(text);
  return Number.isNaN(numeric) ? null : numeric;
}

function escapeHtml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function shortId(value) {
  if (!value) {
    return '—';
  }
  return String(value).slice(0, 8);
}

function formatSmall(value) {
  return Number(value || 0).toFixed(3);
}

async function copyReport() {
  if (!state.report) {
    notify('No report to copy.');
    return;
  }
  await navigator.clipboard.writeText(state.report);
  notify('Report copied.');
}
