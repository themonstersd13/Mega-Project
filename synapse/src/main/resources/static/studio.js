const app = document.getElementById('app');
const moneyFmt = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });
const numberFmt = new Intl.NumberFormat('en-US');
const dateFmt = new Intl.DateTimeFormat('en-US', {
  month: 'short',
  day: 'numeric',
  hour: '2-digit',
  minute: '2-digit'
});

const NAV = [
  { group: 'Overview', items: [{ route: 'overview', label: 'Overview', icon: '◌' }] },
  {
    group: 'Organization',
    items: [
      { route: 'organization', label: 'Canvas', icon: '◇' },
      { route: 'organization/structure', label: 'Structure', icon: '☰' }
    ]
  },
  {
    group: 'Operations',
    items: [
      { route: 'operations/tasks', label: 'Tasks', icon: '↗' },
      { route: 'operations/agents', label: 'Agents', icon: 'A' },
      { route: 'operations/approvals', label: 'Approvals', icon: '✓' }
    ]
  },
  {
    group: 'Intelligence',
    items: [
      { route: 'intelligence/context', label: 'Shared Context', icon: '⟐' },
      { route: 'intelligence/memory', label: 'Memory', icon: 'M' },
      { route: 'intelligence/activity', label: 'Activity', icon: '⋯' }
    ]
  },
  {
    group: 'Evaluation',
    items: [
      { route: 'evaluation/overview', label: 'Overview', icon: '◎' },
      { route: 'evaluation/runs', label: 'Runs', icon: 'R' },
      { route: 'evaluation/benchmarks', label: 'Benchmarks', icon: 'B' },
      { route: 'evaluation/reports', label: 'Reports', icon: '📄' }
    ]
  },
  { group: 'Settings', items: [{ route: 'settings', label: 'Settings', icon: '⚙' }] }
];

const state = {
  apiKey: localStorage.getItem('synapse-api-key') || '',
  workspaces: [],
  selectedWorkspaceId: null,
  snapshot: null,
  evaluation: null,
  route: parseRoute(),
  loading: true,
  toast: '',
  paletteOpen: false,
  paletteQuery: '',
  paletteIndex: 0,
  createOpen: false,
  createTab: 'organization',
  globalSearch: '',
  inspectorTab: 'overview',
  selectedNodeId: null,
  selectedTaskId: null,
  selectedMemoryId: null,
  selectedApprovalId: null,
  selectedAgentId: null,
  canvas: { scale: 1, x: 0, y: 0 },
  canvasDrag: null,
  nodeLayout: {},
  taskFilter: 'all',
  taskSearch: '',
  statusMessage: 'Ready'
};

let renderTimer = null;
let paletteTimer = null;

document.addEventListener('DOMContentLoaded', init);
window.addEventListener('hashchange', () => {
  state.route = parseRoute();
  renderApp();
});
window.addEventListener('keydown', onGlobalKeydown);

function init() {
  if (!app) {
    return;
  }
  app.addEventListener('click', onClick);
  app.addEventListener('submit', onSubmit);
  app.addEventListener('input', onInput);
  app.addEventListener('change', onChange);
  app.addEventListener('dragstart', onDragStart);
  app.addEventListener('dragover', onDragOver);
  app.addEventListener('drop', onDrop);
  app.addEventListener('pointerdown', onPointerDown);
  app.addEventListener('pointermove', onPointerMove);
  app.addEventListener('pointerup', onPointerUp);
  app.addEventListener('wheel', onWheel, { passive: false });
  boot();
}

async function boot() {
  state.loading = true;
  renderApp();
  try {
    await loadWorkspaces();
    if (!state.selectedWorkspaceId && state.workspaces.length > 0) {
      state.selectedWorkspaceId = state.workspaces[0].organizationId || state.workspaces[0].id;
    }
    if (state.selectedWorkspaceId) {
      await loadWorkspace(state.selectedWorkspaceId);
    }
    await loadEvaluation();
    state.statusMessage = 'Connected';
  } catch (error) {
    notify(error.message || 'Unable to load SYNAPSE.');
    state.statusMessage = 'Needs attention';
  } finally {
    state.loading = false;
    ensureDefaults();
    renderApp();
  }
}

function ensureDefaults() {
  if (!state.selectedWorkspaceId && state.workspaces.length > 0) {
    state.selectedWorkspaceId = state.workspaces[0].organizationId || state.workspaces[0].id;
  }
  if (!state.selectedNodeId && state.snapshot) {
    state.selectedNodeId = buildGraph(state.snapshot).nodes[0].id;
  }
  if (currentRoute().indexOf('organization') === 0 && state.snapshot && state.canvas.scale === 1 && state.canvas.x === 0 && state.canvas.y === 0) {
    fitCanvas();
  }
}

function parseRoute() {
  var raw = window.location.hash || '#overview';
  raw = raw.replace(/^#\/?/, '');
  var parts = raw.split('/').filter(Boolean);
  return parts.length > 0 ? parts : ['overview'];
}

function currentRoute() {
  return state.route.length > 0 ? state.route.join('/') : 'overview';
}

function currentWorkspace() {
  if (!state.workspaces.length) {
    return null;
  }
  var selected = state.workspaces.find(function (item) {
    return item.organizationId === state.selectedWorkspaceId || item.id === state.selectedWorkspaceId;
  });
  return selected || state.workspaces[0];
}

function currentSnapshot() {
  return state.snapshot;
}

function routeTo(route) {
  window.location.hash = '#' + route;
}

async function loadWorkspaces() {
  state.workspaces = await api('/workspaces');
}

async function loadWorkspace(id) {
  state.snapshot = await api('/workspaces/' + id);
  state.selectedNodeId = state.selectedNodeId || 'org:' + getSummary().organizationId;
}

async function loadEvaluation() {
  try {
    state.evaluation = await api('/demo/report');
  } catch (error) {
    state.evaluation = null;
  }
}

function scheduleRender(preserveFocus) {
  if (renderTimer) {
    clearTimeout(renderTimer);
  }
  renderTimer = setTimeout(function () {
    renderApp(preserveFocus);
  }, 0);
}

function renderApp(preserveFocus) {
  var focusState = preserveFocus ? captureFocusState() : null;
  try {
    app.innerHTML = buildShell();
    restoreFocusState(focusState);
  } catch (error) {
    app.innerHTML = '<div class="app-shell"><main class="main-scroll"><div class="page"><div class="surface surface-pad"><h1 class="page-title">Studio failed to render</h1><div class="page-subtitle">' + escapeHtml(error && error.message ? error.message : 'Unknown error') + '</div></div></div></main></div>';
    console.error(error);
  }
}

function captureFocusState() {
  var active = document.activeElement;
  if (!active || !app.contains(active) || !('value' in active)) {
    return null;
  }
  return {
    selector: buildFocusSelector(active),
    start: active.selectionStart,
    end: active.selectionEnd
  };
}

function restoreFocusState(stateInfo) {
  if (!stateInfo || !stateInfo.selector) {
    return;
  }
  var element = app.querySelector(stateInfo.selector);
  if (!element) {
    return;
  }
  if (typeof element.focus === 'function') {
    element.focus();
  }
  if (typeof stateInfo.start === 'number' && typeof stateInfo.end === 'number' && element.setSelectionRange) {
    element.setSelectionRange(stateInfo.start, stateInfo.end);
  }
}

function buildFocusSelector(element) {
  if (element.dataset && element.dataset.focusKey) {
    return '[data-focus-key="' + element.dataset.focusKey + '"]';
  }
  if (element.name) {
    return '[name="' + element.name + '"]';
  }
  return '';
}

function buildShell() {
  var workspace = currentWorkspace();
  var route = currentRoute();
  return [
    '<div class="app-shell">',
    renderSidebar(workspace),
    '<div class="workspace-main">',
    renderTopbar(workspace),
    '<main class="main-scroll">',
    '<div class="page">',
    renderPage(route, workspace),
    '</div>',
    '</main>',
    '</div>',
    renderPalette(),
    renderCreateModal(),
    renderToast(),
    '</div>'
  ].join('');
}

function renderSidebar(workspace) {
  return [
    '<aside class="sidebar">',
    '<div class="brand">',
    '<div class="brand-mark">S</div>',
    '<div><div class="brand-name">SYNAPSE</div><div class="brand-sub">Studio control workspace</div></div>',
    '</div>',
    '<div class="workspace-switcher">',
    '<div class="label">Workspace</div>',
    renderWorkspaceSelect(workspace),
    '<div class="status-row"><span>' + escapeHtml(workspace ? workspace.organizationName || workspace.name || 'No workspace' : 'No workspace') + '</span><span class="status-pill">' + escapeHtml(state.statusMessage) + '</span></div>',
    '</div>',
    '<nav class="nav-groups">',
    NAV.map(renderNavGroup).join(''),
    '</nav>',
    '<div class="sidebar-footer">',
    '<div class="status-row"><span class="muted">Environment</span><span class="badge accent">Live APIs</span></div>',
    '<div class="status-row"><span class="muted">Approvals</span><span>' + numberValue((getSnapshot().approvals || []).filter(function (item) { return item.status === 'PENDING'; }).length) + ' pending</span></div>',
    '</div>',
    '</aside>'
  ].join('');
}

function renderWorkspaceSelect(workspace) {
  var options = state.workspaces.map(function (item) {
    var id = item.organizationId || item.id;
    return '<option value="' + escapeHtml(String(id)) + '" ' + (id === state.selectedWorkspaceId ? 'selected' : '') + '>' + escapeHtml(item.organizationName || item.name || 'Workspace') + '</option>';
  }).join('');
  if (!options) {
    options = '<option value="">No workspaces</option>';
  }
  return '<select class="workspace-select" data-action="switch-workspace">' + options + '</select>';
}

function renderNavGroup(group) {
  var items = group.items.map(function (item) {
    var active = isRouteActive(item.route) ? 'active' : '';
    return '<a class="nav-item ' + active + '" href="#' + item.route + '" data-route="' + item.route + '"><span class="nav-icon">' + escapeHtml(item.icon) + '</span><span>' + escapeHtml(item.label) + '</span></a>';
  }).join('');
  return '<section class="nav-group"><div class="nav-group-title">' + escapeHtml(group.group) + '</div><div class="nav-items">' + items + '</div></section>';
}

function renderTopbar(workspace) {
  var crumbs = getBreadcrumbs();
  return [
    '<header class="topbar">',
    '<div class="crumbs">' + crumbs.map(function (crumb, index) {
      var separator = index === 0 ? '' : '<span>›</span>';
      return separator + (crumb.href ? '<a href="#' + crumb.href + '">' + escapeHtml(crumb.label) + '</a>' : '<strong>' + escapeHtml(crumb.label) + '</strong>');
    }).join('') + '</div>',
    '<div class="topbar-actions">',
    '<div class="search-wrap">',
    '<span class="muted">⌕</span>',
    '<input type="search" name="globalSearch" value="' + escapeAttr(state.globalSearch) + '" placeholder="Search organizations, tasks, memory..." data-focus-key="global-search" />',
    '<span class="shortcut">Ctrl K</span>',
    '</div>',
    '<button class="icon-button" type="button" data-action="open-palette">⌘ Palette</button>',
    '<button class="icon-button" type="button" data-action="open-create">+ Create</button>',
    '<button class="icon-button" type="button" data-route="operations/approvals">✓ ' + numberValue(pendingApprovalsCount()) + '</button>',
    '</div>',
    '</header>'
  ].join('');
}

function renderPage(route) {
  if (state.loading) {
    return renderLoadingPage();
  }
  if (!currentWorkspace()) {
    return renderEmptyWorkspace();
  }
  if (route === 'overview') {
    return renderOverviewPage();
  }
  if (route === 'organization' || route === 'organization/structure') {
    return renderOrganizationPage(route);
  }
  if (route.indexOf('operations/') === 0) {
    return renderOperationsPage(route);
  }
  if (route.indexOf('intelligence/') === 0) {
    return renderIntelligencePage(route);
  }
  if (route.indexOf('evaluation/') === 0) {
    return renderEvaluationPage(route);
  }
  if (route === 'settings') {
    return renderSettingsPage();
  }
  return renderOverviewPage();
}

function renderLoadingPage() {
  return [
    '<div class="page-header">',
    '<div><h1 class="page-title">Loading SYNAPSE Studio</h1><div class="page-subtitle">Preparing workspace, board, and evaluation data.</div></div>',
    '</div>',
    '<div class="summary-grid">',
    skeletonMetric(), skeletonMetric(), skeletonMetric(), skeletonMetric(),
    '</div>',
    '<div class="content-grid">',
    '<div class="surface surface-pad"><div class="loading">' + skeletonLine(92) + skeletonLine(68) + skeletonLine(84) + '</div></div>',
    '<div class="surface surface-pad"><div class="loading">' + skeletonLine(92) + skeletonLine(68) + skeletonLine(84) + '</div></div>',
    '</div>'
  ].join('');
}

function skeletonMetric() {
  return '<div class="metric"><div class="skeleton" style="width:38%;height:12px"></div><div class="skeleton" style="width:56%;height:28px;margin-top:14px"></div><div class="skeleton" style="width:72%;height:12px;margin-top:14px"></div></div>';
}

function skeletonLine(width) {
  return '<div class="skeleton" style="width:' + width + '%"></div>';
}

function renderEmptyWorkspace() {
  return [
    '<div class="page-header">',
    '<div><h1 class="page-title">No workspace yet</h1><div class="page-subtitle">Create an organization to start managing teams, agents, tasks, memory, and evaluations.</div></div>',
    '<div class="page-actions"><button class="button primary" type="button" data-action="open-create">Create organization</button><button class="button" type="button" data-action="seed-demo">Seed demo</button></div>',
    '</div>',
    '<div class="surface surface-pad empty-state">SYNAPSE needs an organization before the canvas and operations views can populate.</div>'
  ].join('');
}

function renderOverviewPage() {
  var snapshot = getSnapshot();
  var summary = getSummary();
  var evaluation = state.evaluation || {};
  var benchmark = evaluation.benchmark || null;
  var criteria = evaluation.criteria || [];
  return [
    renderPageHeader(
      summary.organizationName || 'Workspace',
      statusLabel(summary),
      'Control what is happening in ' + escapeHtml(summary.organizationName || 'the workspace') + ' right now.',
      [
        '<button class="button" type="button" data-route="organization">Open organization</button>',
        '<button class="button primary" type="button" data-action="open-create">Create...</button>'
      ].join('')
    ),
    renderSummaryGrid([
      metricCard('Budget', moneyValue(summary.spentBudgetUsd) + ' / ' + moneyValue(summary.totalBudgetUsd), summary.budgetStatus || 'UNKNOWN'),
      metricCard('Agents', numberValue(summary.agentCount), numberValue(summary.activeTaskCount) + ' active tasks'),
      metricCard('Tasks', numberValue(summary.taskCount), numberValue(summary.blockedTaskCount) + ' blocked'),
      metricCard('Approvals', numberValue(pendingApprovalsCount()), 'Needs review')
    ]),
    '<div class="content-grid">',
    '<section class="surface surface-pad">',
    surfaceHeader('Live operations', 'Agents, tasks, and blockers at a glance'),
    renderLiveOperations(snapshot),
    '</section>',
    '<section class="surface surface-pad">',
    surfaceHeader('Attention', 'Pending approvals and blocked work'),
    renderAttention(snapshot),
    '</section>',
    '</div>',
    '<div class="content-grid">',
    '<section class="surface surface-pad">',
    surfaceHeader('Recent activity', 'Latest messages and operational events'),
    renderActivityFeed(snapshot, 8),
    '</section>',
    '<section class="surface surface-pad">',
    surfaceHeader('Evaluation snapshot', 'Current benchmark summary'),
    renderEvaluationSnapshot(benchmark, criteria, evaluation.prediction),
    '</section>',
    '</div>',
    '<section class="surface surface-pad">',
    surfaceHeader('Organization preview', 'Teams, roles, and agents'),
    renderOrgPreview(snapshot),
    '</section>'
  ].join('');
}

function renderOrganizationPage(route) {
  if (route === 'organization/structure') {
    return renderStructurePage();
  }
  if (state.canvas.scale === 1 && state.canvas.x === 0 && state.canvas.y === 0) {
    fitCanvas();
  }
  var snapshot = getSnapshot();
  var graph = buildGraph(snapshot);
  if (!state.selectedNodeId || !graph.nodeMap[state.selectedNodeId]) {
    state.selectedNodeId = graph.nodes.length ? graph.nodes[0].id : null;
  }
  var selected = state.selectedNodeId ? graph.nodeMap[state.selectedNodeId] : null;
  return [
    renderPageHeader(
      'Organization',
      'Canvas and structure',
      'Pan, zoom, inspect, and manage the organization graph. Relationship changes are surfaced only where the backend can persist them.',
      [
        '<button class="button" type="button" data-canvas-control="fit">Fit to screen</button>',
        '<button class="button" type="button" data-canvas-control="center">Center selection</button>',
        '<button class="button primary" type="button" data-action="open-create" data-create-tab="team">Add team</button>'
      ].join('')
    ),
    '<div class="surface surface-pad">',
    '<div class="canvas-toolbar">',
    '<div class="canvas-controls">',
    '<span class="toolbar-chip">Nodes ' + numberValue(graph.nodes.length) + '</span>',
    '<span class="toolbar-chip">Roles ' + numberValue(snapshot.roles.length) + '</span>',
    '<span class="toolbar-chip">Teams ' + numberValue(snapshot.teams.length) + '</span>',
    '<span class="toolbar-chip">Agents ' + numberValue(snapshot.agents.length) + '</span>',
    '</div>',
    '<div class="canvas-controls">',
    '<input class="input" type="search" placeholder="Search canvas nodes" name="canvasSearch" value="' + escapeAttr(state.globalSearch) + '" data-focus-key="canvas-search" />',
    '</div>',
    '</div>',
    '<div class="split-layout">',
    '<div>',
    renderCanvas(graph, selected),
    '</div>',
    renderInspector(selected, graph),
    '</div>',
    '</div>'
  ].join('');
}

function renderStructurePage() {
  var snapshot = getSnapshot();
  var summary = getSummary();
  return [
    renderPageHeader(
      'Organization structure',
      'Hierarchy and roster',
      'A compact view of the org, teams, roles, agents, goals, and task distribution.',
      '<button class="button primary" type="button" data-route="organization">Open canvas</button>'
    ),
    '<div class="content-grid">',
    '<section class="surface surface-pad">',
    surfaceHeader('Hierarchy', 'Organization tree'),
    '<div class="list">',
    '<div class="item-card"><div class="item-title">' + escapeHtml(summary.organizationName || 'Organization') + '</div><div class="item-meta">' + escapeHtml(statusLabel(summary)) + '</div></div>',
    (snapshot.teams || []).map(function (team) {
      return '<div class="item-card"><div class="item-title">' + escapeHtml(team.name) + '</div><div class="item-meta">Team · ' + escapeHtml(team.topology || 'n/a') + ' · ' + numberValue((snapshot.agents || []).filter(function (agent) { return agent.teamId === team.id; }).length) + ' agents</div></div>';
    }).join(''),
    '</div>',
    '</section>',
    '<section class="surface surface-pad">',
    surfaceHeader('Roster', 'Roles and agents'),
    '<div class="content-grid">',
    '<div class="surface surface-pad">',
    surfaceHeader('Roles', 'Capabilities'),
    renderChips((snapshot.roles || []).map(function (role) { return role.name; })),
    '</div>',
    '<div class="surface surface-pad">',
    surfaceHeader('Agents', 'Operational state'),
    snapshot.agents && snapshot.agents.length ? renderAgentTable(snapshot.agents, snapshot) : '<div class="empty-state">No agents.</div>',
    '</div>',
    '</div>',
    '</section>',
    '</div>'
  ].join('');
}

function renderOperationsPage(route) {
  if (route === 'operations/tasks') {
    return renderTasksPage();
  }
  if (route === 'operations/agents') {
    return renderAgentsPage();
  }
  return renderApprovalsPage();
}

function renderTasksPage() {
  var snapshot = getSnapshot();
  var board = snapshot.board || { lanes: {} };
  var lanes = board.lanes || {};
  return [
    renderPageHeader(
      'Tasks',
      'Board and detail drawer',
      'Move tasks across workflow lanes, assign agents, and inspect dependencies without leaving the operations surface.',
      [
        '<button class="button" type="button" data-action="open-create" data-create-tab="goal">Create goal</button>',
        '<button class="button primary" type="button" data-route="organization">Open organization</button>'
      ].join('')
    ),
    '<div class="surface surface-pad">',
    '<div class="canvas-toolbar">',
    '<div class="canvas-controls">',
    '<input class="input" type="search" name="taskSearch" value="' + escapeAttr(state.taskSearch) + '" placeholder="Search tasks" />',
    '<select class="select" name="taskFilter">',
    option('all', 'All tasks', state.taskFilter),
    option('PENDING', 'Pending', state.taskFilter),
    option('ASSIGNED', 'Assigned', state.taskFilter),
    option('IN_PROGRESS', 'Working', state.taskFilter),
    option('COMPLETED', 'Done', state.taskFilter),
    option('FAILED', 'Failed', state.taskFilter),
    option('BLOCKED', 'Blocked', state.taskFilter),
    '</select>',
    '</div>',
    '<div class="canvas-controls"><span class="toolbar-chip">Drag cards between lanes to update status</span></div>',
    '</div>',
    '<div class="split-layout">',
    '<div>',
    renderTaskBoard(lanes),
    '</div>',
    renderTaskDrawer(selectedTask(snapshot)),
    '</div>',
    '</div>'
  ].join('');
}

function renderAgentsPage() {
  var snapshot = getSnapshot();
  var agents = filteredAgents(snapshot.agents || []);
  return [
    renderPageHeader(
      'Agents',
      'Team workload and usage',
      'Review agent status, role, workload, token usage, and last activity in a dense operational table.',
      '<button class="button primary" type="button" data-route="organization">Open canvas</button>'
    ),
    '<section class="surface surface-pad">',
    surfaceHeader('Agent roster', 'Current operational state'),
    agents.length ? renderAgentTable(agents, snapshot) : '<div class="empty-state">No agents match the current search.</div>',
    '</section>'
  ].join('');
}

function renderApprovalsPage() {
  var snapshot = getSnapshot();
  var approvals = filteredApprovals(snapshot.approvals || []);
  var selected = selectedApproval(snapshot);
  return [
    renderPageHeader(
      'Approvals',
      'Pending and resolved decisions',
      'Approve or reject requested actions, then inspect the related task and risk context.',
      '<button class="button primary" type="button" data-route="overview">Back to overview</button>'
    ),
    '<div class="split-layout">',
    '<section class="surface surface-pad">',
    surfaceHeader('Approval queue', 'Pending, approved, and rejected items'),
    approvals.length ? renderApprovalTable(approvals, snapshot) : '<div class="empty-state">No approvals match the current search.</div>',
    '</section>',
    renderApprovalDrawer(selected, snapshot),
    '</div>'
  ].join('');
}

function renderIntelligencePage(route) {
  if (route === 'intelligence/memory') {
    return renderMemoryPage();
  }
  if (route === 'intelligence/activity') {
    return renderActivityPage();
  }
  return renderContextPage();
}

function renderContextPage() {
  var snapshot = getSnapshot();
  return [
    renderPageHeader(
      'Shared context',
      'Organization and team context',
      'Review recent messages, shared memory, and approvals tied to the current workspace.',
      '<button class="button primary" type="button" data-route="organization">Open canvas</button>'
    ),
    '<div class="content-grid">',
    '<section class="surface surface-pad">',
    surfaceHeader('Messages', 'Recent shared context'),
    renderMessages(snapshot.messages || []),
    '</section>',
    '<section class="surface surface-pad">',
    surfaceHeader('Memory', 'Recalled and shared decisions'),
    renderMemoryList((snapshot.memories || []).slice(0, 10)),
    '</section>',
    '</div>'
  ].join('');
}

function renderMemoryPage() {
  var snapshot = getSnapshot();
  var memories = filteredMemories(snapshot.memories || []);
  var selected = selectedMemory(snapshot);
  return [
    renderPageHeader(
      'Memory',
      'Searchable memory workspace',
      'Filter memories by source, scope, and confidence, then explain a recalled decision in context.',
      '<button class="button primary" type="button" data-action="seed-demo">Seed demo</button>'
    ),
    '<div class="split-layout">',
    '<section class="surface surface-pad">',
    '<div class="canvas-toolbar"><div class="canvas-controls"><input class="input" type="search" name="memorySearch" value="' + escapeAttr(state.globalSearch) + '" placeholder="Search memory" /></div></div>',
    memories.length ? renderMemoryList(memories) : '<div class="empty-state">No memories match the current search.</div>',
    '</section>',
    renderMemoryDrawer(selected),
    '</div>'
  ].join('');
}

function renderActivityPage() {
  var snapshot = getSnapshot();
  return [
    renderPageHeader(
      'Activity',
      'Operational feed',
      'A consolidated timeline of messages, approvals, and recent state changes.',
      '<button class="button primary" type="button" data-route="overview">Back to overview</button>'
    ),
    '<section class="surface surface-pad">',
    surfaceHeader('Timeline', 'Recent motion across the workspace'),
    renderActivityFeed(snapshot, 24),
    '</section>'
  ].join('');
}

function renderEvaluationPage(route) {
  var evaluation = state.evaluation || {};
  var benchmark = evaluation.benchmark || null;
  var criteria = evaluation.criteria || [];
  if (route === 'evaluation/runs') {
    return renderEvaluationRunsPage(benchmark);
  }
  if (route === 'evaluation/benchmarks') {
    return renderEvaluationBenchmarksPage(benchmark);
  }
  if (route === 'evaluation/reports') {
    return renderEvaluationReportsPage(evaluation);
  }
  return [
    renderPageHeader(
      'Evaluation',
      'Runs, benchmarks, reports',
      'Track whether batching reduces calls and cost, and inspect the structured report generated by the demo flow.',
      '<button class="button primary" type="button" data-action="run-evaluation">Run evaluation</button>'
    ),
    renderSummaryGrid(renderEvaluationMetrics(benchmark)),
    '<div class="content-grid">',
    '<section class="surface surface-pad">',
    surfaceHeader('Benchmark summary', 'Mean results across runs'),
    renderBenchmarkSummary(benchmark),
    '</section>',
    '<section class="surface surface-pad">',
    surfaceHeader('Acceptance criteria', 'Pass/fail coverage'),
    renderCriteria(criteria),
    '</section>',
    '</div>',
    '<section class="surface surface-pad">',
    surfaceHeader('Structured report', 'Latest generated evaluation report'),
    renderReportView(evaluation),
    '</section>'
  ].join('');
}

function renderEvaluationRunsPage(benchmark) {
  return [
    renderPageHeader('Runs', 'Repeated benchmark execution', 'The benchmark harness repeats the same workload to quantify cost and latency reductions.', '<button class="button primary" type="button" data-route="evaluation/overview">Back</button>'),
    '<section class="surface surface-pad">',
    surfaceHeader('Run statistics', 'Naive vs batched'),
    renderBenchmarkSummary(benchmark),
    '</section>'
  ].join('');
}

function renderEvaluationBenchmarksPage(benchmark) {
  return [
    renderPageHeader('Benchmarks', 'Performance and reduction metrics', 'Compare call count, token usage, latency, and batch fill ratio.', '<button class="button primary" type="button" data-route="evaluation/overview">Back</button>'),
    '<section class="surface surface-pad">',
    surfaceHeader('Metrics', 'One glance summary'),
    renderBenchmarkSummary(benchmark),
    '</section>'
  ].join('');
}

function renderEvaluationReportsPage(evaluation) {
  return [
    renderPageHeader('Reports', 'Structured evaluation output', 'The generated report is kept readable and copyable for sharing.', '<button class="button primary" type="button" data-action="copy-report">Copy report</button>'),
    renderReportView(evaluation)
  ].join('');
}

function renderSettingsPage() {
  var workspace = currentWorkspace();
  return [
    renderPageHeader(
      'Settings',
      'Studio configuration',
      'Store the API key, manage demo data, and check the currently loaded workspace.',
      '<button class="button primary" type="button" data-action="save-settings">Save settings</button>'
    ),
    '<div class="content-grid">',
    '<section class="surface surface-pad">',
    surfaceHeader('API access', 'Backend authentication and runtime'),
    '<form class="modal-grid" data-form="settings">',
    field('API key', '<input class="input" type="password" name="apiKey" value="' + escapeAttr(state.apiKey) + '" placeholder="X-API-Key" data-focus-key="api-key" />'),
    '</form>',
    '<div class="pill-row"><span class="badge accent">Workspace: ' + escapeHtml(workspace ? workspace.organizationName || workspace.name || 'Unknown' : 'None') + '</span><span class="badge">' + escapeHtml(state.statusMessage) + '</span></div>',
    '</section>',
    '<section class="surface surface-pad">',
    surfaceHeader('Demo and evaluation', 'Seed, evaluate, and report'),
    '<div class="pill-row">',
    '<button class="button" type="button" data-action="seed-demo">Seed demo</button>',
    '<button class="button primary" type="button" data-action="run-evaluation">Run evaluation</button>',
    '<button class="button" type="button" data-action="copy-report">Copy report</button>',
    '</div>',
    '</section>',
    '</div>'
  ].join('');
}

function renderPageHeader(title, eyebrow, subtitle, actions) {
  return [
    '<div class="page-header">',
    '<div>',
    '<h1 class="page-title">' + escapeHtml(title) + '</h1>',
    '<div class="page-subtitle"><span class="badge accent">' + escapeHtml(eyebrow) + '</span><div style="margin-top:10px">' + escapeHtml(subtitle) + '</div></div>',
    '</div>',
    '<div class="page-actions">' + actions + '</div>',
    '</div>'
  ].join('');
}

function renderSummaryGrid(cards) {
  return '<div class="summary-grid">' + cards.join('') + '</div>';
}

function metricCard(label, value, meta) {
  return [
    '<div class="metric">',
    '<div class="kicker">' + escapeHtml(label) + '</div>',
    '<div class="value">' + value + '</div>',
    '<div class="meta">' + escapeHtml(meta) + '</div>',
    '</div>'
  ].join('');
}

function surfaceHeader(title, subtitle) {
  return [
    '<div class="surface-header">',
    '<div><h2 class="surface-title">' + escapeHtml(title) + '</h2><div class="surface-subtitle">' + escapeHtml(subtitle) + '</div></div>',
    '</div>'
  ].join('');
}

function renderLiveOperations(snapshot) {
  var agents = filteredAgents(snapshot.agents || []);
  var board = snapshot.board || { lanes: {} };
  var lanes = board.lanes || {};
  return [
    '<div class="list">',
    '<div class="item-card">',
    '<div class="item-row"><div><div class="item-title">Running agents</div><div class="item-meta">' + numberValue(agents.filter(function (item) { return String(item.status || '').toUpperCase() === 'ACTIVE' || String(item.status || '').toUpperCase() === 'IN_PROGRESS'; }).length) + ' agents active</div></div><span class="badge good">Healthy</span></div>',
    '</div>',
    '<div class="item-card">',
    '<div class="item-row"><div><div class="item-title">Queued work</div><div class="item-meta">' + numberValue(countLane(lanes, 'PENDING') + countLane(lanes, 'ASSIGNED')) + ' tasks waiting or assigned</div></div><span class="badge warn">Flow</span></div>',
    '</div>',
    '<div class="item-card">',
    '<div class="item-row"><div><div class="item-title">Blocked work</div><div class="item-meta">' + numberValue(countLane(lanes, 'BLOCKED')) + ' tasks blocked</div></div><span class="badge bad">Attention</span></div>',
    '</div>',
    '</div>'
  ].join('');
}

function renderAttention(snapshot) {
  var blockedTasks = (snapshot.tasks || []).filter(function (task) { return String(task.status || '').toUpperCase() === 'BLOCKED'; }).slice(0, 5);
  var pendingApprovals = (snapshot.approvals || []).filter(function (approval) { return String(approval.status || '').toUpperCase() === 'PENDING'; }).slice(0, 5);
  return [
    '<div class="list">',
    sectionList('Blocked tasks', blockedTasks, function (task) {
      return '<div class="item-card clickable" data-task-id="' + escapeHtml(String(task.id)) + '"><div class="item-title">' + escapeHtml(task.description || 'Task') + '</div><div class="item-meta">' + renderTaskMeta(task) + '</div></div>';
    }, 'No blocked tasks'),
    sectionList('Pending approvals', pendingApprovals, function (approval) {
      return '<div class="item-card clickable" data-approval-id="' + escapeHtml(String(approval.id)) + '"><div class="item-title">' + escapeHtml(approval.actionDescription || 'Approval') + '</div><div class="item-meta">' + escapeHtml(statusLabel(approval)) + '</div></div>';
    }, 'No pending approvals')
    ,
    '</div>'
  ].join('');
}

function renderActivityFeed(snapshot, limit) {
  var items = [];
  (snapshot.messages || []).slice(0, limit).forEach(function (message) {
    items.push({
      type: 'message',
      time: message.sentAt || message.createdAt,
      label: 'Message',
      title: previewText(message.content),
      meta: message.traceId ? 'Trace ' + shortId(message.traceId) : 'Run ' + shortId(message.runId || '')
    });
  });
  (snapshot.approvals || []).forEach(function (approval) {
    items.push({
      type: 'approval',
      time: approval.createdAt,
      label: 'Approval',
      title: approval.actionDescription || 'Approval request',
      meta: approval.status || ''
    });
  });
  items.sort(function (a, b) {
    return new Date(b.time || 0).getTime() - new Date(a.time || 0).getTime();
  });
  items = items.slice(0, limit);
  if (!items.length) {
    return '<div class="empty-state">No activity yet.</div>';
  }
  return '<div class="timeline">' + items.map(function (item) {
    return '<div class="timeline-item"><div class="item-meta">' + escapeHtml(item.label) + ' · ' + escapeHtml(formatDate(item.time)) + '</div><div class="item-title" style="margin-top:4px">' + escapeHtml(item.title) + '</div><div class="item-meta">' + escapeHtml(item.meta) + '</div></div>';
  }).join('') + '</div>';
}

function renderEvaluationSnapshot(benchmark, criteria, prediction) {
  if (!benchmark) {
    return '<div class="empty-state">No benchmark has been run yet.</div>';
  }
  return [
    '<div class="list">',
    '<div class="item-card"><div class="item-title">Mean cost reduction</div><div class="item-meta">' + numberValue(benchmark.meanCostReductionPercent) + '% ± ' + numberValue(benchmark.stdDevCostReductionPercent) + '%</div></div>',
    '<div class="item-card"><div class="item-title">Mean batched calls</div><div class="item-meta">' + numberValue(benchmark.meanBatchedApiCalls) + ' vs ' + numberValue(benchmark.meanNaiveApiCalls) + ' naive</div></div>',
    '<div class="item-card"><div class="item-title">Predicted cost</div><div class="item-meta">' + (prediction ? moneyValue(prediction.predictedCostUsd) : 'n/a') + (prediction && prediction.fallbackUsed ? ' · fallback' : '') + '</div></div>',
    '<div class="item-card"><div class="item-title">Acceptance criteria</div><div class="item-meta">' + criteria.length + ' checked</div></div>',
    '</div>'
  ].join('');
}

function renderOrgPreview(snapshot) {
  var teams = snapshot.teams || [];
  var roles = snapshot.roles || [];
  var agents = snapshot.agents || [];
  if (!teams.length && !roles.length && !agents.length) {
    return '<div class="empty-state">No organization structure available.</div>';
  }
  return [
    '<div class="content-grid">',
    '<div class="surface surface-pad">',
    surfaceHeader('Teams', 'Structure'),
    teams.length ? renderChips(teams.map(function (team) { return team.name; })) : '<div class="empty-state">No teams yet.</div>',
    '</div>',
    '<div class="surface surface-pad">',
    surfaceHeader('Roles', 'Capabilities'),
    roles.length ? renderChips(roles.map(function (role) { return role.name; })) : '<div class="empty-state">No roles yet.</div>',
    '</div>',
    '</div>',
    '<div style="height:12px"></div>',
    '<div class="surface surface-pad">',
    surfaceHeader('Agents', 'Current roster'),
    agents.length ? renderAgentTable(agents.slice(0, 6), snapshot) : '<div class="empty-state">No agents yet.</div>',
    '</div>'
  ].join('');
}

function renderCanvas(graph, selected) {
  var stageStyle = 'transform: translate(' + state.canvas.x + 'px, ' + state.canvas.y + 'px) scale(' + state.canvas.scale + ');';
  return [
    '<div class="canvas-shell" data-canvas-shell>',
    '<div class="canvas-stage" style="' + stageStyle + '" data-canvas-stage>',
    '<div class="canvas-edges">' + renderEdges(graph) + '</div>',
    '<div class="canvas-nodes">' + graph.nodes.map(function (node) {
      var active = selected && selected.id === node.id ? 'active' : '';
      return '<div class="node ' + node.kind + ' ' + active + '" data-node-id="' + escapeHtml(node.id) + '" style="left:' + node.x + 'px;top:' + node.y + 'px;width:' + node.width + 'px;height:' + node.height + 'px">' +
        '<div class="node-kicker">' + escapeHtml(node.kind) + '</div>' +
        '<div class="node-name">' + escapeHtml(node.title) + '</div>' +
        '<div class="node-meta">' + escapeHtml(node.meta) + '</div>' +
        '<div class="node-footer"><span>' + escapeHtml(node.badge) + '</span><span class="status-dot ' + node.statusTone + '"></span></div>' +
        '</div>';
    }).join('') + '</div>',
    '</div>',
    renderMinimap(graph),
    '</div>'
  ].join('');
}

function renderEdges(graph) {
  var edges = graph.edges.map(function (edge) {
    var from = graph.nodeMap[edge.from];
    var to = graph.nodeMap[edge.to];
    if (!from || !to) {
      return '';
    }
    var fromCenterX = from.x + from.width / 2;
    var fromCenterY = from.y + from.height / 2;
    var toCenterX = to.x + to.width / 2;
    var toCenterY = to.y + to.height / 2;
    var midY = (fromCenterY + toCenterY) / 2;
    var path = 'M ' + fromCenterX + ' ' + (fromCenterY + 2) + ' C ' + fromCenterX + ' ' + midY + ', ' + toCenterX + ' ' + midY + ', ' + toCenterX + ' ' + (toCenterY - 2);
    return '<path d="' + path + '" fill="none" stroke="rgba(124,92,255,0.35)" stroke-width="2" />';
  }).join('');
  return '<svg viewBox="0 0 ' + graph.width + ' ' + graph.height + '">' + edges + '</svg>';
}

function renderMinimap(graph) {
  var scale = Math.min(180 / graph.width, 120 / graph.height);
  var nodes = graph.nodes.map(function (node) {
    return '<div class="minimap-node" style="left:' + (node.x * scale) + 'px;top:' + (node.y * scale) + 'px;width:' + Math.max(5, node.width * scale) + 'px;height:' + Math.max(4, node.height * scale) + 'px"></div>';
  }).join('');
  var viewportWidth = 180 / state.canvas.scale;
  var viewportHeight = 120 / state.canvas.scale;
  var viewportLeft = Math.max(0, -state.canvas.x * scale);
  var viewportTop = Math.max(0, -state.canvas.y * scale);
  return [
    '<div class="minimap">',
    '<div class="minimap-inner" style="transform: scale(' + scale + ')">',
    nodes,
    '<div class="minimap-viewport" style="left:' + viewportLeft + 'px;top:' + viewportTop + 'px;width:' + viewportWidth + 'px;height:' + viewportHeight + 'px"></div>',
    '</div>',
    '</div>'
  ].join('');
}

function renderInspector(selected, graph) {
  if (!selected) {
    return '<aside class="drawer"><div class="drawer-header"><div><div class="surface-title">Inspector</div><div class="surface-subtitle">Select a node</div></div></div><div class="drawer-body"><div class="empty-state">Choose an organization, team, role, agent, or goal to inspect details.</div></div></aside>';
  }
  return [
    '<aside class="drawer">',
    '<div class="drawer-header">',
    '<div><div class="surface-title">' + escapeHtml(selected.title) + '</div><div class="surface-subtitle">' + escapeHtml(selected.kind) + '</div></div>',
    '<div class="pill-row"><button class="button" type="button" data-canvas-control="center">Center</button><button class="button" type="button" data-action="open-create" data-create-tab="' + inspectorCreateTab(selected.kind) + '">Create</button></div>',
    '</div>',
    '<div class="drawer-body">',
    renderInspectorTabs(),
    renderInspectorPanel(selected, graph),
    '</div>',
    '</aside>'
  ].join('');
}

function renderInspectorTabs() {
  var tabs = ['overview', 'context', 'memory', 'tasks', 'activity'];
  return '<div class="tabs" style="margin-bottom:14px">' + tabs.map(function (tab) {
    return '<button class="tab ' + (state.inspectorTab === tab ? 'active' : '') + '" type="button" data-inspector-tab="' + tab + '">' + escapeHtml(tab) + '</button>';
  }).join('') + '</div>';
}

function renderInspectorPanel(selected, graph) {
  if (state.inspectorTab === 'context') {
    return renderNodeContext(selected);
  }
  if (state.inspectorTab === 'memory') {
    return renderNodeMemory(selected);
  }
  if (state.inspectorTab === 'tasks') {
    return renderNodeTasks(selected);
  }
  if (state.inspectorTab === 'activity') {
    return renderNodeActivity(selected);
  }
  return renderNodeOverview(selected, graph);
}

function renderNodeOverview(selected) {
  var lines = selected.lines || [];
  return [
    '<div class="list">',
    '<div class="item-card"><div class="item-title">Summary</div><div class="item-meta">' + escapeHtml(selected.meta) + '</div></div>',
    lines.map(function (line) { return '<div class="item-card"><div class="item-title">' + escapeHtml(line.label) + '</div><div class="item-meta">' + escapeHtml(line.value) + '</div></div>'; }).join(''),
    '</div>'
  ].join('');
}

function renderNodeContext(selected) {
  var messages = relatedMessages(selected).slice(0, 8);
  if (!messages.length) {
    return '<div class="empty-state">No related messages found.</div>';
  }
  return '<div class="timeline">' + messages.map(function (message) {
    return '<div class="timeline-item"><div class="item-meta">' + escapeHtml(formatDate(message.sentAt || message.createdAt)) + '</div><div class="item-title" style="margin-top:4px">' + escapeHtml(previewText(message.content)) + '</div><div class="item-meta">' + escapeHtml(shortId(message.runId || message.traceId || '')) + '</div></div>';
  }).join('') + '</div>';
}

function renderNodeMemory(selected) {
  var memories = relatedMemories(selected).slice(0, 8);
  if (!memories.length) {
    return '<div class="empty-state">No related memories found.</div>';
  }
  return renderMemoryList(memories);
}

function renderNodeTasks(selected) {
  var tasks = relatedTasks(selected).slice(0, 8);
  if (!tasks.length) {
    return '<div class="empty-state">No related tasks found.</div>';
  }
  return '<div class="list">' + tasks.map(function (task) {
    return '<div class="item-card clickable" data-task-id="' + escapeHtml(String(task.id)) + '"><div class="item-title">' + escapeHtml(task.description || 'Task') + '</div><div class="item-meta">' + renderTaskMeta(task) + '</div></div>';
  }).join('') + '</div>';
}

function renderNodeActivity(selected) {
  var items = [];
  relatedMessages(selected).slice(0, 5).forEach(function (message) {
    items.push('<div class="timeline-item"><div class="item-meta">' + escapeHtml(formatDate(message.sentAt || message.createdAt)) + '</div><div class="item-title" style="margin-top:4px">' + escapeHtml(previewText(message.content)) + '</div></div>');
  });
  relatedMemories(selected).slice(0, 5).forEach(function (memory) {
    items.push('<div class="timeline-item"><div class="item-meta">' + escapeHtml(formatDate(memory.createdAt)) + '</div><div class="item-title" style="margin-top:4px">' + escapeHtml(previewText(memory.content)) + '</div></div>');
  });
  if (!items.length) {
    return '<div class="empty-state">No activity yet.</div>';
  }
  return '<div class="timeline">' + items.join('') + '</div>';
}

function renderTaskBoard(lanes) {
  var search = (state.taskSearch || state.globalSearch || '').toLowerCase();
  var visibleStatuses = state.taskFilter === 'all' ? null : [state.taskFilter];
  var order = ['PENDING', 'ASSIGNED', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'BLOCKED'];
  return '<div class="board">' + order.map(function (laneKey) {
    var laneTasks = (lanes[laneKey] || []).filter(function (task) {
      var matchesSearch = !search || String(task.description || '').toLowerCase().indexOf(search) >= 0 || String(task.assignedAgentName || '').toLowerCase().indexOf(search) >= 0;
      var matchesFilter = !visibleStatuses || visibleStatuses.indexOf(String(task.status || '').toUpperCase()) >= 0;
      return matchesSearch && matchesFilter;
    });
    return '<div class="lane" data-drop-status="' + laneKey + '"><div class="lane-title"><h3>' + escapeHtml(laneLabel(laneKey)) + '</h3><span class="lane-count">' + numberValue(laneTasks.length) + '</span></div><div class="cards">' + (laneTasks.length ? laneTasks.map(renderTaskCard).join('') : '<div class="empty-state">Drop tasks here</div>') + '</div></div>';
  }).join('') + '</div>';
}

function renderTaskCard(task) {
  var status = String(task.status || '').toUpperCase();
  return [
    '<div class="task-card" draggable="true" data-task-id="' + escapeHtml(String(task.id)) + '">',
    '<div class="task-title">' + escapeHtml(task.description || 'Task') + '</div>',
    '<div class="task-meta">',
    '<span class="tag">' + escapeHtml(status) + '</span>',
    task.assignedAgentName ? '<span class="tag">' + escapeHtml(task.assignedAgentName) + '</span>' : '',
    task.priority != null ? '<span class="tag">P' + escapeHtml(String(task.priority)) + '</span>' : '',
    task.requiredSkillTags ? task.requiredSkillTags.slice(0, 2).map(function (tag) { return '<span class="tag">' + escapeHtml(tag) + '</span>'; }).join('') : '',
    '</div>',
    '</div>'
  ].join('');
}

function renderTaskDrawer(task) {
  if (!task) {
    return '<aside class="drawer"><div class="drawer-header"><div><div class="surface-title">Task detail</div><div class="surface-subtitle">Select a task</div></div></div><div class="drawer-body"><div class="empty-state">Click a task card to open its drawer.</div></div></aside>';
  }
  var snapshot = getSnapshot();
  var agents = snapshot.agents || [];
  return [
    '<aside class="drawer">',
    '<div class="drawer-header"><div><div class="surface-title">' + escapeHtml(task.description || 'Task') + '</div><div class="surface-subtitle">' + escapeHtml(task.status || '') + '</div></div></div>',
    '<div class="drawer-body">',
    taskTabs(),
    '<div class="list">',
    taskTabContent(task, agents, snapshot),
    '</div>',
    '</div>',
    '</aside>'
  ].join('');
}

function taskTabs() {
  var tabs = ['overview', 'dependencies', 'context', 'memory', 'activity', 'approvals'];
  return '<div class="tabs" style="margin-bottom:14px">' + tabs.map(function (tab) {
    return '<button class="tab ' + (state.inspectorTab === tab ? 'active' : '') + '" type="button" data-inspector-tab="' + tab + '">' + escapeHtml(tab) + '</button>';
  }).join('') + '</div>';
}

function taskTabContent(task, agents, snapshot) {
  if (state.inspectorTab === 'dependencies') {
    var children = (snapshot.tasks || []).filter(function (item) { return item.parentTaskId === task.id; });
    return children.length ? children.map(function (child) { return '<div class="item-card clickable" data-task-id="' + escapeHtml(String(child.id)) + '"><div class="item-title">' + escapeHtml(child.description || 'Task') + '</div><div class="item-meta">' + renderTaskMeta(child) + '</div></div>'; }).join('') : '<div class="empty-state">No task dependencies found.</div>';
  }
  if (state.inspectorTab === 'context') {
    return renderTaskContext(task, snapshot);
  }
  if (state.inspectorTab === 'memory') {
    return renderTaskMemory(task, snapshot);
  }
  if (state.inspectorTab === 'activity') {
    return renderTaskActivity(task, snapshot);
  }
  if (state.inspectorTab === 'approvals') {
    return renderTaskApprovals(task, snapshot);
  }
  return renderTaskOverview(task, agents);
}

function renderTaskOverview(task, agents) {
  return [
    '<div class="item-card">',
    '<div class="item-title">Overview</div>',
    '<div class="item-meta">' + escapeHtml(renderTaskMeta(task)) + '</div>',
    '</div>',
    '<div class="item-card">',
    '<div class="item-title">Assign agent</div>',
    '<form data-form="assign-task" data-task-id="' + escapeHtml(String(task.id)) + '" class="modal-grid">',
    '<select class="select" name="assignedAgentId">',
    '<option value="">Select agent</option>',
    agents.map(function (agent) { return '<option value="' + escapeHtml(String(agent.id)) + '"' + (task.assignedAgentId === agent.id ? ' selected' : '') + '>' + escapeHtml((agent.roleName || 'Agent') + (agent.teamName ? ' · ' + agent.teamName : '')) + '</option>'; }).join(''),
    '</select>',
    '<div class="modal-actions" style="padding:0; border:0; justify-content:flex-start">',
    '<button class="button primary" type="submit">Assign</button>',
    '</div>',
    '</form>',
    '</div>',
    '<div class="item-card"><div class="item-title">Status controls</div><div class="pill-row">' + taskStatusButtons(task) + '</div></div>'
  ].join('');
}

function renderTaskContext(task, snapshot) {
  var related = (snapshot.messages || []).filter(function (message) {
    return String(message.runId || '') === String(task.id) || String(message.traceId || '') === String(task.id);
  });
  if (!related.length) {
    related = (snapshot.messages || []).slice(0, 6);
  }
  return related.length ? renderMessages(related) : '<div class="empty-state">No message context for this task.</div>';
}

function renderTaskMemory(task, snapshot) {
  var memories = (snapshot.memories || []).filter(function (memory) { return memory.taskId === task.id; });
  return memories.length ? renderMemoryList(memories) : '<div class="empty-state">No task-linked memories found.</div>';
}

function renderTaskActivity(task, snapshot) {
  return '<div class="timeline"><div class="timeline-item"><div class="item-meta">' + escapeHtml(formatDate(task.createdAt)) + '</div><div class="item-title" style="margin-top:4px">Task created</div></div>' + (task.updatedAt ? '<div class="timeline-item"><div class="item-meta">' + escapeHtml(formatDate(task.updatedAt)) + '</div><div class="item-title" style="margin-top:4px">Last updated</div></div>' : '') + '</div>';
}

function renderTaskApprovals(task, snapshot) {
  var approvals = (snapshot.approvals || []).filter(function (approval) { return approval.taskId === task.id; });
  return approvals.length ? approvals.map(function (approval) {
    return '<div class="item-card clickable" data-approval-id="' + escapeHtml(String(approval.id)) + '"><div class="item-title">' + escapeHtml(approval.actionDescription || 'Approval') + '</div><div class="item-meta">' + escapeHtml(approval.status || '') + '</div></div>';
  }).join('') : '<div class="empty-state">No approvals for this task.</div>';
}

function renderAgentTable(agents, snapshot) {
  var tasks = snapshot.tasks || [];
  return [
    '<table class="table">',
    '<thead><tr><th>Agent</th><th>Role</th><th>Team</th><th>Status</th><th>Current task</th><th>Tokens</th><th>Last active</th></tr></thead>',
    '<tbody>',
    agents.map(function (agent) {
      var currentTask = tasks.find(function (task) { return task.assignedAgentId === agent.id; });
      return '<tr data-agent-id="' + escapeHtml(String(agent.id)) + '"><td>' + escapeHtml(agent.roleName || 'Agent') + '</td><td>' + escapeHtml(agent.roleName || '') + '</td><td>' + escapeHtml(agent.teamName || '—') + '</td><td><span class="badge ' + toneForStatus(agent.status) + '">' + escapeHtml(String(agent.status || 'UNKNOWN')) + '</span></td><td>' + escapeHtml(currentTask ? previewText(currentTask.description) : 'Idle') + '</td><td>' + numberValue(agent.tokensUsed) + '</td><td>' + escapeHtml(formatDate(agent.lastActiveAt)) + '</td></tr>';
    }).join(''),
    '</tbody>',
    '</table>'
  ].join('');
}

function renderApprovalTable(approvals, snapshot) {
  var tasks = snapshot.tasks || [];
  var agents = snapshot.agents || [];
  return [
    '<table class="table">',
    '<thead><tr><th>Action</th><th>Agent</th><th>Task</th><th>Risk</th><th>Status</th><th>Created</th></tr></thead>',
    '<tbody>',
    approvals.map(function (approval) {
      var task = tasks.find(function (item) { return item.id === approval.taskId; });
      var agent = task ? agents.find(function (item) { return item.id === task.assignedAgentId; }) : null;
      return '<tr data-approval-id="' + escapeHtml(String(approval.id)) + '"><td>' + escapeHtml(approval.actionDescription || '') + '</td><td>' + escapeHtml(agent ? (agent.roleName || 'Agent') : '—') + '</td><td>' + escapeHtml(task ? previewText(task.description) : '—') + '</td><td>' + escapeHtml(String(approval.effectType || '')) + ' · confidence ' + escapeHtml(String(approval.confidence == null ? 'n/a' : approval.confidence)) + '</td><td><span class="badge ' + toneForApproval(approval.status) + '">' + escapeHtml(String(approval.status || '')) + '</span></td><td>' + escapeHtml(formatDate(approval.createdAt)) + '</td></tr>';
    }).join(''),
    '</tbody>',
    '</table>'
  ].join('');
}

function renderApprovalDrawer(approval, snapshot) {
  if (!approval) {
    return '<aside class="drawer"><div class="drawer-header"><div><div class="surface-title">Approval detail</div><div class="surface-subtitle">Select an approval</div></div></div><div class="drawer-body"><div class="empty-state">Open an approval to review context.</div></div></aside>';
  }
  var task = (snapshot.tasks || []).find(function (item) { return item.id === approval.taskId; });
  return [
    '<aside class="drawer">',
    '<div class="drawer-header"><div><div class="surface-title">' + escapeHtml(approval.actionDescription || 'Approval') + '</div><div class="surface-subtitle">' + escapeHtml(approval.status || '') + '</div></div></div>',
    '<div class="drawer-body">',
    '<div class="list">',
    '<div class="item-card"><div class="item-title">Related task</div><div class="item-meta">' + escapeHtml(task ? task.description : '—') + '</div></div>',
    '<div class="item-card"><div class="item-title">Context</div><div class="item-meta">' + escapeHtml(String(approval.effectType || '')) + ' · confidence ' + escapeHtml(String(approval.confidence == null ? 'n/a' : approval.confidence)) + '</div></div>',
    '<div class="item-card"><div class="pill-row"><button class="button primary" type="button" data-approval-decision="APPROVED" data-approval-id="' + escapeHtml(String(approval.id)) + '">Approve</button><button class="button" type="button" data-approval-decision="REJECTED" data-approval-id="' + escapeHtml(String(approval.id)) + '">Reject</button></div></div>',
    '</div>',
    '</div>',
    '</aside>'
  ].join('');
}

function renderMemoryList(memories) {
  return '<div class="list">' + memories.map(function (memory) {
    return '<div class="item-card clickable" data-memory-id="' + escapeHtml(String(memory.id)) + '"><div class="item-row"><div><div class="item-title">' + escapeHtml(previewText(memory.content)) + '</div><div class="item-meta">' + escapeHtml(memory.scope || '') + ' · ' + escapeHtml(String(memory.confidence == null ? 'n/a' : memory.confidence)) + '</div></div><span class="badge">' + escapeHtml(memory.superseded ? 'superseded' : 'live') + '</span></div></div>';
  }).join('') + '</div>';
}

function renderMemoryDrawer(memory) {
  if (!memory) {
    return '<aside class="drawer"><div class="drawer-header"><div><div class="surface-title">Memory detail</div><div class="surface-subtitle">Select a memory</div></div></div><div class="drawer-body"><div class="empty-state">Open a memory to inspect evidence and explanation.</div></div></aside>';
  }
  return [
    '<aside class="drawer">',
    '<div class="drawer-header"><div><div class="surface-title">' + escapeHtml(previewText(memory.content)) + '</div><div class="surface-subtitle">' + escapeHtml(memory.scope || '') + '</div></div></div>',
    '<div class="drawer-body">',
    '<div class="list">',
    '<div class="item-card"><div class="item-title">Confidence</div><div class="item-meta">' + escapeHtml(String(memory.confidence == null ? 'n/a' : memory.confidence)) + '</div></div>',
    '<div class="item-card"><div class="item-title">Source links</div><div class="item-meta">Organization ' + escapeHtml(shortId(memory.organizationId || '')) + '<br/>Agent ' + escapeHtml(shortId(memory.agentId || '')) + '<br/>Task ' + escapeHtml(shortId(memory.taskId || '')) + '</div></div>',
    '<div class="item-card"><button class="button primary" type="button" data-explain-memory="' + escapeHtml(String(memory.id)) + '">Explain</button></div>',
    '</div>',
    renderMemoryExplain(),
    '</div>',
    '</aside>'
  ].join('');
}

function renderMemoryExplain() {
  if (!state.explain) {
    return '';
  }
  var explain = state.explain;
  return [
    '<div class="surface surface-pad" style="margin-top:14px">',
    surfaceHeader('Explanation', 'Related decision chain'),
    '<div class="list">',
    '<div class="item-card"><div class="item-title">Decision</div><div class="item-meta">' + escapeHtml(explain.decision ? previewText(explain.decision.content || explain.decision) : '—') + '</div></div>',
    '<div class="item-card"><div class="item-title">Evidence</div><div class="item-meta">' + ((explain.evidence || []).map(function (item) { return escapeHtml(previewText(item.content || '')); }).join('<br/>') || '—') + '</div></div>',
    '</div>',
    '</div>'
  ].join('');
}

function renderMessages(messages) {
  if (!messages || !messages.length) {
    return '<div class="empty-state">No messages found.</div>';
  }
  return '<div class="timeline">' + messages.map(function (message) {
    return '<div class="timeline-item"><div class="item-meta">' + escapeHtml(formatDate(message.sentAt || message.createdAt)) + '</div><div class="item-title" style="margin-top:4px">' + escapeHtml(previewText(message.content)) + '</div><div class="item-meta">' + escapeHtml(shortId(message.runId || message.traceId || '')) + '</div></div>';
  }).join('') + '</div>';
}

function renderCriteria(criteria) {
  if (!criteria || !criteria.length) {
    return '<div class="empty-state">No criteria yet.</div>';
  }
  return '<div class="list">' + criteria.map(function (criterion) {
    return '<div class="item-card"><div class="item-row"><div><div class="item-title">' + escapeHtml(criterion.name) + '</div><div class="item-meta">' + escapeHtml(criterion.details) + '</div></div><span class="badge ' + (criterion.passed ? 'good' : 'bad') + '">' + (criterion.passed ? 'PASS' : 'FAIL') + '</span></div></div>';
  }).join('') + '</div>';
}

function renderBenchmarkSummary(benchmark) {
  if (!benchmark) {
    return '<div class="empty-state">No benchmark data yet.</div>';
  }
  return renderSummaryGrid([
    metricCard('Naive calls', numberValue(benchmark.meanNaiveApiCalls), '± ' + numberValue(benchmark.stdDevNaiveApiCalls)),
    metricCard('Batched calls', numberValue(benchmark.meanBatchedApiCalls), '± ' + numberValue(benchmark.stdDevBatchedApiCalls)),
    metricCard('Naive cost', moneyValue(benchmark.meanNaiveCostUsd), '± ' + moneyValue(benchmark.stdDevNaiveCostUsd)),
    metricCard('Batched cost', moneyValue(benchmark.meanBatchedCostUsd), '± ' + moneyValue(benchmark.stdDevBatchedCostUsd))
  ]);
}

function renderEvaluationMetrics(benchmark) {
  if (!benchmark) {
    return [
      metricCard('Runs', '—', 'Awaiting evaluation'),
      metricCard('Tasks', '—', 'Awaiting evaluation'),
      metricCard('Calls', '—', 'Awaiting evaluation'),
      metricCard('Cost reduction', '—', 'Awaiting evaluation')
    ];
  }
  return [
    metricCard('Runs', numberValue(benchmark.runs), 'Harness repetitions'),
    metricCard('Tasks', numberValue(benchmark.taskCount), 'Benchmark workload'),
    metricCard('Calls', numberValue(benchmark.meanBatchedApiCalls), 'Batched mean'),
    metricCard('Cost reduction', numberValue(benchmark.meanCostReductionPercent) + '%', 'Mean reduction')
  ];
}

function renderReportView(evaluation) {
  if (!evaluation || !evaluation.reportMarkdown) {
    return '<div class="empty-state">No report has been generated yet.</div>';
  }
  return '<div class="report-view"><div class="report-raw">' + escapeHtml(evaluation.reportMarkdown) + '</div></div>';
}

function renderToast() {
  return state.toast ? '<div class="toast">' + escapeHtml(state.toast) + '</div>' : '';
}

function renderPalette() {
  if (!state.paletteOpen) {
    return '';
  }
  var items = paletteItems().filter(function (item) {
    var query = state.paletteQuery.toLowerCase();
    return !query || item.label.toLowerCase().indexOf(query) >= 0 || item.category.toLowerCase().indexOf(query) >= 0;
  });
  if (!items.length) {
    items = [{ label: 'No matches', category: 'Search', action: function () {} }];
  }
  return [
    '<div class="overlay" data-action="close-palette"></div>',
    '<div class="command-palette">',
    '<input class="command-input" type="search" value="' + escapeAttr(state.paletteQuery) + '" placeholder="Search entities and actions" name="paletteQuery" data-focus-key="palette-query" />',
    '<div class="command-results">',
    items.map(function (item, index) {
      var active = index === state.paletteIndex ? 'active' : '';
      return '<div class="command-item ' + active + '" data-command-index="' + index + '"><div><div class="command-label">' + escapeHtml(item.label) + '</div><div class="command-kicker">' + escapeHtml(item.category) + '</div></div><span class="badge">' + escapeHtml(item.kind) + '</span></div>';
    }).join(''),
    '</div>',
    '</div>'
  ].join('');
}

function renderCreateModal() {
  if (!state.createOpen) {
    return '';
  }
  return [
    '<div class="backdrop" data-action="close-create"></div>',
    '<div class="modal">',
    '<div class="modal-header"><div><div class="surface-title">Create</div><div class="surface-subtitle">Guided creation flow</div></div><button class="icon-button" type="button" data-action="close-create">×</button></div>',
    '<div class="modal-body">',
    '<div class="tabs" style="margin-bottom:16px">',
    modalTab('organization', 'Organization'),
    modalTab('team', 'Team'),
    modalTab('role', 'Role'),
    modalTab('goal', 'Goal'),
    '</div>',
    createTabContent(),
    '</div>',
    '</div>'
  ].join('');
}

function modalTab(key, label) {
  return '<button class="tab ' + (state.createTab === key ? 'active' : '') + '" type="button" data-create-tab="' + key + '">' + escapeHtml(label) + '</button>';
}

function createTabContent() {
  if (state.createTab === 'team') {
    return createForm('team', [
      field('Name', '<input class="input" name="name" required />'),
      field('Topology', '<select class="select" name="teamTopology"><option value="HIERARCHICAL">Hierarchical</option><option value="MATRIX">Matrix</option><option value="FLAT">Flat</option></select>')
    ]);
  }
  if (state.createTab === 'role') {
    return createForm('role', [
      field('Name', '<input class="input" name="name" required />'),
      field('Model tier', '<select class="select" name="tier"><option value="HAIKU">HAIKU</option><option value="SONNET">SONNET</option><option value="OPUS">OPUS</option></select>'),
      field('Token budget', '<input class="input" name="tokenBudget" type="number" min="1" />'),
      field('System prompt', '<textarea class="textarea" name="systemPrompt" rows="5"></textarea>'),
      field('Responsibilities', '<textarea class="textarea" name="responsibilities" rows="4" placeholder="One responsibility per line"></textarea>')
    ]);
  }
  if (state.createTab === 'goal') {
    return createForm('goal', [
      field('Description', '<textarea class="textarea" name="description" rows="4" required></textarea>')
    ]);
  }
  return createForm('organization', [
    field('Name', '<input class="input" name="name" required />'),
    field('Budget', '<input class="input" name="budget" type="number" min="0" step="0.01" />')
  ]);
}

function createForm(kind, fields) {
  return [
    '<form class="modal-grid" data-form="create-' + kind + '">',
    fields.join(''),
    '<div class="modal-actions">',
    '<button class="button" type="button" data-action="close-create">Cancel</button>',
    '<button class="button primary" type="submit">Create</button>',
    '</div>',
    '</form>'
  ].join('');
}

function field(label, control) {
  return '<label class="modal-grid"><span class="panel-label">' + escapeHtml(label) + '</span>' + control + '</label>';
}

function buildGraph(snapshot) {
  snapshot = snapshot || {};
  var summary = getSummary();
  var roles = snapshot.roles || [];
  var teams = snapshot.teams || [];
  var agents = snapshot.agents || [];
  var goals = snapshot.goals || [];
  var nodes = [];
  var edges = [];
  var width = 1320;
  var height = 760;
  var orgId = 'org:' + (summary.organizationId || 'org');
  var orgLayout = state.nodeLayout[orgId] || {};
  var orgNode = {
    id: orgId,
    rawId: summary.organizationId || null,
    kind: 'org',
    title: summary.organizationName || 'Organization',
    meta: 'Budget ' + moneyValue(summary.totalBudgetUsd),
    badge: summary.budgetStatus || 'ORG',
    statusTone: toneForBudget(summary.budgetStatus),
    x: orgLayout.x != null ? orgLayout.x : 510,
    y: orgLayout.y != null ? orgLayout.y : 40,
    width: 260,
    height: 100,
    lines: [
      { label: 'Budget', value: moneyValue(summary.spentBudgetUsd) + ' / ' + moneyValue(summary.totalBudgetUsd) },
      { label: 'Tasks', value: numberValue(summary.taskCount) }
    ]
  };
  nodes.push(orgNode);
  var teamSpacing = teams.length > 0 ? Math.floor(1000 / Math.max(teams.length, 1)) : 250;
  teams.forEach(function (team, index) {
    var id = 'team:' + team.id;
    var layout = state.nodeLayout[id] || {};
    var node = {
      id: id,
      rawId: team.id,
      kind: 'team',
      title: team.name,
      meta: team.topology || 'Team',
      badge: 'Team',
      statusTone: 'good',
      x: layout.x != null ? layout.x : 140 + (index * teamSpacing),
      y: layout.y != null ? layout.y : 200,
      width: 220,
      height: 92,
      lines: [
        { label: 'Topology', value: team.topology || 'n/a' },
        { label: 'Agents', value: numberValue(agents.filter(function (agent) { return agent.teamId === team.id; }).length) }
      ]
    };
    nodes.push(node);
    edges.push({ from: orgId, to: id });
    agents.filter(function (agent) { return agent.teamId === team.id; }).forEach(function (agent, agentIndex) {
      var agentId = 'agent:' + agent.id;
      var agentLayout = state.nodeLayout[agentId] || {};
      var agentNode = {
        id: agentId,
        rawId: agent.id,
        kind: 'agent',
        title: agent.roleName || 'Agent',
        meta: (agent.teamName || 'Team') + ' · ' + (agent.status || 'UNKNOWN'),
        badge: shortId(agent.id),
        statusTone: toneForStatus(agent.status),
        x: agentLayout.x != null ? agentLayout.x : 110 + (index * teamSpacing) + (agentIndex * 18),
        y: agentLayout.y != null ? agentLayout.y : 360 + (agentIndex * 108),
        width: 230,
        height: 96,
        lines: [
          { label: 'Role', value: agent.roleName || 'n/a' },
          { label: 'Tokens', value: numberValue(agent.tokensUsed) }
        ]
      };
      nodes.push(agentNode);
      edges.push({ from: id, to: agentId });
    });
  });
  roles.forEach(function (role, index) {
    var roleId = 'role:' + role.id;
    var roleLayout = state.nodeLayout[roleId] || {};
    var node = {
      id: roleId,
      rawId: role.id,
      kind: 'role',
      title: role.name,
      meta: role.modelTier || 'Role',
      badge: 'Role',
      statusTone: 'accent',
      x: roleLayout.x != null ? roleLayout.x : 1030,
      y: roleLayout.y != null ? roleLayout.y : 190 + (index * 110),
      width: 220,
      height: 92,
      lines: [
        { label: 'Model tier', value: role.modelTier || 'n/a' },
        { label: 'Token budget', value: numberValue(role.tokenBudget) }
      ]
    };
    nodes.push(node);
    edges.push({ from: orgId, to: roleId });
    agents.filter(function (agent) { return agent.roleId === role.id; }).forEach(function (agent) {
      edges.push({ from: roleId, to: 'agent:' + agent.id });
    });
  });
  goals.forEach(function (goal, index) {
    var goalId = 'goal:' + goal.id;
    var goalLayout = state.nodeLayout[goalId] || {};
    nodes.push({
      id: goalId,
      rawId: goal.id,
      kind: 'goal',
      title: previewText(goal.description),
      meta: goal.status || 'Goal',
      badge: 'Goal',
      statusTone: toneForStatus(goal.status),
      x: goalLayout.x != null ? goalLayout.x : 180 + (index * 250),
      y: goalLayout.y != null ? goalLayout.y : 620,
      width: 230,
      height: 88,
      lines: [
        { label: 'Tasks', value: numberValue((goal.tasks || []).length) },
        { label: 'Status', value: goal.status || 'n/a' }
      ]
    });
    edges.push({ from: orgId, to: goalId });
  });
  return {
    nodes: nodes,
    edges: edges,
    nodeMap: nodes.reduce(function (memo, node) {
      memo[node.id] = node;
      return memo;
    }, {}),
    width: width,
    height: height
  };
}

function buildPaletteItems() {
  var workspace = currentWorkspace();
  var items = [
    { label: 'Open overview', category: 'Navigate', kind: 'Route', action: function () { routeTo('overview'); } },
    { label: 'Open organization canvas', category: 'Navigate', kind: 'Route', action: function () { routeTo('organization'); } },
    { label: 'Open tasks board', category: 'Navigate', kind: 'Route', action: function () { routeTo('operations/tasks'); } },
    { label: 'Open approvals', category: 'Navigate', kind: 'Route', action: function () { routeTo('operations/approvals'); } },
    { label: 'Open memory', category: 'Navigate', kind: 'Route', action: function () { routeTo('intelligence/memory'); } },
    { label: 'Open evaluation', category: 'Navigate', kind: 'Route', action: function () { routeTo('evaluation/overview'); } },
    { label: 'Seed demo workspace', category: 'Action', kind: 'Action', action: seedDemo },
    { label: 'Run evaluation', category: 'Action', kind: 'Action', action: runEvaluation }
  ];
  if (workspace) {
    items.push(
      { label: 'Create organization', category: 'Create', kind: 'Form', action: function () { openCreate('organization'); } },
      { label: 'Create team', category: 'Create', kind: 'Form', action: function () { openCreate('team'); } },
      { label: 'Create role', category: 'Create', kind: 'Form', action: function () { openCreate('role'); } },
      { label: 'Create goal', category: 'Create', kind: 'Form', action: function () { openCreate('goal'); } }
    );
  }
  (state.snapshot && state.snapshot.tasks || []).slice(0, 20).forEach(function (task) {
    items.push({ label: 'Task: ' + previewText(task.description), category: 'Task', kind: 'Open', action: function () { state.selectedTaskId = task.id; routeTo('operations/tasks'); } });
  });
  (state.snapshot && state.snapshot.agents || []).slice(0, 20).forEach(function (agent) {
    items.push({ label: 'Agent: ' + previewText(agent.roleName), category: 'Agent', kind: 'Open', action: function () { state.selectedAgentId = agent.id; routeTo('operations/agents'); } });
  });
  return items;
}

function paletteItems() {
  return buildPaletteItems();
}

function getFilteredPaletteItems() {
  var items = paletteItems();
  var query = state.paletteQuery.toLowerCase();
  return items.filter(function (item) {
    return !query || item.label.toLowerCase().indexOf(query) >= 0 || item.category.toLowerCase().indexOf(query) >= 0;
  });
}

function pendingApprovalsCount() {
  return (getSnapshot().approvals || []).filter(function (item) { return String(item.status || '').toUpperCase() === 'PENDING'; }).length;
}

function getSummary() {
  return getSnapshot().summary || {};
}

function getSnapshot() {
  return state.snapshot || { summary: {}, roles: [], teams: [], agents: [], goals: [], tasks: [], messages: [], memories: [], approvals: [], board: { lanes: {} } };
}

function selectedTask(snapshot) {
  var tasks = snapshot.tasks || [];
  if (state.selectedTaskId) {
    var found = tasks.find(function (task) { return task.id === state.selectedTaskId; });
    if (found) {
      return found;
    }
  }
  return tasks.length ? tasks[0] : null;
}

function selectedApproval(snapshot) {
  var approvals = snapshot.approvals || [];
  if (state.selectedApprovalId) {
    var found = approvals.find(function (approval) { return approval.id === state.selectedApprovalId; });
    if (found) {
      return found;
    }
  }
  return approvals.length ? approvals[0] : null;
}

function selectedMemory(snapshot) {
  var memories = snapshot.memories || [];
  if (state.selectedMemoryId) {
    var found = memories.find(function (memory) { return memory.id === state.selectedMemoryId; });
    if (found) {
      return found;
    }
  }
  return memories.length ? memories[0] : null;
}

function filteredAgents(agents) {
  var query = (state.globalSearch || '').toLowerCase();
  if (!query) {
    return agents;
  }
  return agents.filter(function (agent) {
    return contains(agent.roleName, query) || contains(agent.teamName, query) || contains(agent.status, query);
  });
}

function filteredApprovals(approvals) {
  var query = (state.globalSearch || '').toLowerCase();
  if (!query) {
    return approvals;
  }
  return approvals.filter(function (approval) {
    return contains(approval.actionDescription, query) || contains(approval.status, query);
  });
}

function filteredMemories(memories) {
  var query = (state.globalSearch || '').toLowerCase();
  if (!query) {
    return memories;
  }
  return memories.filter(function (memory) {
    return contains(memory.content, query) || contains(memory.scope, query);
  });
}

function relatedTasks(selected) {
  var snapshot = getSnapshot();
  var tasks = snapshot.tasks || [];
  if (!selected) {
    return tasks;
  }
  if (selected.kind === 'org') {
    return tasks;
  }
  if (selected.kind === 'team') {
    var agentIds = (snapshot.agents || []).filter(function (agent) { return agent.teamId === selected.rawId; }).map(function (agent) { return agent.id; });
    return tasks.filter(function (task) {
      return agentIds.indexOf(task.assignedAgentId) >= 0;
    });
  }
  if (selected.kind === 'role') {
    var roleAgents = (snapshot.agents || []).filter(function (agent) { return agent.roleId === selected.rawId; }).map(function (agent) { return agent.id; });
    return tasks.filter(function (task) { return roleAgents.indexOf(task.assignedAgentId) >= 0; });
  }
  if (selected.kind === 'agent') {
    return tasks.filter(function (task) { return task.assignedAgentId === selected.rawId; });
  }
  if (selected.kind === 'goal') {
    return tasks.filter(function (task) { return task.goalId === selected.rawId; });
  }
  return [];
}

function relatedMessages(selected) {
  var snapshot = getSnapshot();
  var messages = snapshot.messages || [];
  if (!selected) {
    return messages;
  }
  if (selected.kind === 'agent') {
    return messages.filter(function (message) {
      return message.senderAgentId === selected.rawId || message.recipientAgentId === selected.rawId;
    });
  }
  if (selected.kind === 'role') {
    var agents = snapshot.agents.filter(function (agent) { return agent.roleId === selected.rawId; }).map(function (agent) { return agent.id; });
    return messages.filter(function (message) { return agents.indexOf(message.senderAgentId) >= 0 || agents.indexOf(message.recipientAgentId) >= 0; });
  }
  if (selected.kind === 'team') {
    var teamAgents = snapshot.agents.filter(function (agent) { return agent.teamId === selected.rawId; }).map(function (agent) { return agent.id; });
    return messages.filter(function (message) { return teamAgents.indexOf(message.senderAgentId) >= 0 || teamAgents.indexOf(message.recipientAgentId) >= 0; });
  }
  return messages;
}

function relatedMemories(selected) {
  var snapshot = getSnapshot();
  var memories = snapshot.memories || [];
  if (!selected) {
    return memories;
  }
  if (selected.kind === 'agent') {
    return memories.filter(function (memory) { return memory.agentId === selected.rawId; });
  }
  if (selected.kind === 'goal') {
    return memories.filter(function (memory) { return memory.taskId && (snapshot.tasks || []).some(function (task) { return task.id === memory.taskId && task.goalId === selected.rawId; }); });
  }
  if (selected.kind === 'org') {
    return memories;
  }
  return memories.filter(function (memory) {
    return memory.organizationId === getSummary().organizationId;
  });
}

function renderTaskMeta(task) {
  return [
    task.status || 'UNKNOWN',
    task.assignedAgentName || 'Unassigned',
    task.goalId ? 'Goal ' + shortId(task.goalId) : 'No goal'
  ].join(' · ');
}

function taskStatusButtons(task) {
  var statuses = ['PENDING', 'ASSIGNED', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'BLOCKED'];
  return statuses.map(function (status) {
    return '<button class="button" type="button" data-task-status="' + status + '" data-task-id="' + escapeHtml(String(task.id)) + '">' + escapeHtml(laneLabel(status)) + '</button>';
  }).join('');
}

function laneLabel(lane) {
  if (lane === 'PENDING') return 'Backlog';
  if (lane === 'ASSIGNED') return 'Assigned';
  if (lane === 'IN_PROGRESS') return 'Working';
  if (lane === 'COMPLETED') return 'Done';
  if (lane === 'FAILED') return 'Failed';
  if (lane === 'BLOCKED') return 'Blocked';
  return lane;
}

function countLane(lanes, key) {
  return (lanes[key] || []).length;
}

function statusLabel(item) {
  return String(item.status || item.budgetStatus || 'UNKNOWN').replace(/_/g, ' ');
}

function statusLabelForApproval(status) {
  return String(status || '').replace(/_/g, ' ');
}

function inspectorCreateTab(kind) {
  if (kind === 'team') return 'team';
  if (kind === 'role') return 'role';
  if (kind === 'goal') return 'goal';
  return 'organization';
}

function openCreate(tab) {
  state.createTab = tab || 'organization';
  state.createOpen = true;
  state.paletteOpen = false;
  scheduleRender(true);
}

function closeCreate() {
  state.createOpen = false;
  scheduleRender();
}

function openPalette() {
  state.paletteOpen = true;
  state.paletteQuery = '';
  state.paletteIndex = 0;
  scheduleRender(true);
}

function closePalette() {
  state.paletteOpen = false;
  scheduleRender();
}

function notify(message) {
  state.toast = message;
  scheduleRender();
  setTimeout(function () {
    if (state.toast === message) {
      state.toast = '';
      scheduleRender();
    }
  }, 2500);
}

function isRouteActive(route) {
  return currentRoute() === route || currentRoute().indexOf(route + '/') === 0;
}

function getBreadcrumbs() {
  var route = currentRoute().split('/');
  var items = [{ label: 'SYNAPSE', href: 'overview' }];
  if (route[0] === 'overview') {
    items.push({ label: 'Overview' });
  } else if (route[0] === 'organization') {
    items.push({ label: 'Organization', href: 'organization' });
    if (route[1] === 'structure') items.push({ label: 'Structure' });
    else items.push({ label: 'Canvas' });
  } else if (route[0] === 'operations') {
    items.push({ label: 'Operations', href: 'operations/tasks' });
    items.push({ label: titleCase(route[1] || 'tasks') });
  } else if (route[0] === 'intelligence') {
    items.push({ label: 'Intelligence', href: 'intelligence/context' });
    items.push({ label: titleCase(route[1] || 'context') });
  } else if (route[0] === 'evaluation') {
    items.push({ label: 'Evaluation', href: 'evaluation/overview' });
    items.push({ label: titleCase(route[1] || 'overview') });
  } else if (route[0] === 'settings') {
    items.push({ label: 'Settings' });
  }
  return items;
}

function titleCase(value) {
  value = String(value || '');
  return value.charAt(0).toUpperCase() + value.slice(1);
}

function renderChips(items) {
  if (!items.length) {
    return '<div class="empty-state">None</div>';
  }
  return '<div class="pill-row">' + items.map(function (item) {
    return '<span class="badge accent">' + escapeHtml(item) + '</span>';
  }).join('') + '</div>';
}

function sectionList(title, items, renderer, empty) {
  return '<div class="item-card"><div class="item-title">' + escapeHtml(title) + '</div><div class="list">' + (items.length ? items.map(renderer).join('') : '<div class="empty-state">' + escapeHtml(empty) + '</div>') + '</div></div>';
}

function renderCriteriaPlaceholder() {
  return '<div class="empty-state">No data.</div>';
}

function onGlobalKeydown(event) {
  if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
    event.preventDefault();
    openPalette();
    return;
  }
  if (event.key === 'Escape') {
    if (state.paletteOpen) {
      closePalette();
    }
    if (state.createOpen) {
      closeCreate();
    }
  }
  if (state.paletteOpen && (event.key === 'ArrowDown' || event.key === 'ArrowUp' || event.key === 'Enter')) {
    var items = getFilteredPaletteItems();
    if (!items.length) return;
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      state.paletteIndex = (state.paletteIndex + 1) % items.length;
      scheduleRender(true);
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      state.paletteIndex = (state.paletteIndex - 1 + items.length) % items.length;
      scheduleRender(true);
    } else if (event.key === 'Enter') {
      event.preventDefault();
      runPaletteItem(items[state.paletteIndex] || items[0]);
    }
  }
}

function onClick(event) {
  var target = event.target;
  var action = target.closest('[data-action]');
  if (action) {
    var value = action.getAttribute('data-action');
    if (value === 'open-palette') {
      openPalette();
      return;
    }
    if (value === 'close-palette') {
      closePalette();
      return;
    }
    if (value === 'open-create') {
      openCreate(action.getAttribute('data-create-tab') || 'organization');
      return;
    }
    if (value === 'close-create') {
      closeCreate();
      return;
    }
    if (value === 'seed-demo') {
      seedDemo();
      return;
    }
    if (value === 'run-evaluation') {
      runEvaluation();
      return;
    }
    if (value === 'copy-report') {
      copyReport();
      return;
    }
    if (value === 'save-settings') {
      saveSettings();
      return;
    }
  }

  var command = target.closest('[data-command-index]');
  if (command) {
    var index = Number(command.getAttribute('data-command-index'));
    var items = getFilteredPaletteItems();
    if (items[index]) {
      runPaletteItem(items[index]);
    }
    return;
  }

  var node = target.closest('[data-node-id]');
  if (node) {
    state.selectedNodeId = node.getAttribute('data-node-id');
    state.inspectorTab = 'overview';
    scheduleRender();
    return;
  }

  var task = target.closest('[data-task-id]');
  if (task) {
    state.selectedTaskId = task.getAttribute('data-task-id');
    state.inspectorTab = 'overview';
    scheduleRender();
    return;
  }

  var approval = target.closest('[data-approval-id]');
  if (approval) {
    state.selectedApprovalId = approval.getAttribute('data-approval-id');
    scheduleRender();
    return;
  }

  var memory = target.closest('[data-memory-id]');
  if (memory) {
    state.selectedMemoryId = memory.getAttribute('data-memory-id');
    scheduleRender();
    return;
  }

  var tab = target.closest('[data-inspector-tab]');
  if (tab) {
    state.inspectorTab = tab.getAttribute('data-inspector-tab');
    scheduleRender();
    return;
  }

  var canvasControl = target.closest('[data-canvas-control]');
  if (canvasControl) {
    var control = canvasControl.getAttribute('data-canvas-control');
    if (control === 'fit') {
      fitCanvas();
    } else if (control === 'center') {
      centerSelectedNode();
    }
    scheduleRender();
    return;
  }

  var taskStatus = target.closest('[data-task-status]');
  if (taskStatus) {
    updateTask(taskStatus.getAttribute('data-task-id'), taskStatus.getAttribute('data-task-status'));
    return;
  }

  var approvalDecision = target.closest('[data-approval-decision]');
  if (approvalDecision) {
    resolveApproval(approvalDecision.getAttribute('data-approval-id'), approvalDecision.getAttribute('data-approval-decision'));
    return;
  }

  var explain = target.closest('[data-explain-memory]');
  if (explain) {
    explainMemory(explain.getAttribute('data-explain-memory'));
    return;
  }

  var route = target.closest('[data-route]');
  if (route) {
    routeTo(route.getAttribute('data-route'));
    return;
  }
}

function onSubmit(event) {
  var form = event.target;
  if (!form.matches('form')) {
    return;
  }
  event.preventDefault();
  var formType = form.getAttribute('data-form');
  if (formType === 'create-organization') {
    createOrganization(form);
    return;
  }
  if (formType === 'create-team') {
    createTeam(form);
    return;
  }
  if (formType === 'create-role') {
    createRole(form);
    return;
  }
  if (formType === 'create-goal') {
    createGoal(form);
    return;
  }
  if (formType === 'assign-task') {
    assignTask(form);
    return;
  }
  if (formType === 'settings') {
    saveSettings(form);
  }
}

function onInput(event) {
  var target = event.target;
  if (target.name === 'globalSearch') {
    state.globalSearch = target.value;
    scheduleRender(true);
    return;
  }
  if (target.name === 'canvasSearch') {
    state.globalSearch = target.value;
    scheduleRender(true);
    return;
  }
  if (target.name === 'taskSearch') {
    state.taskSearch = target.value;
    scheduleRender(true);
    return;
  }
  if (target.name === 'memorySearch') {
    state.globalSearch = target.value;
    scheduleRender(true);
    return;
  }
  if (target.name === 'paletteQuery') {
    state.paletteQuery = target.value;
    if (state.paletteIndex >= getFilteredPaletteItems().length) {
      state.paletteIndex = 0;
    }
    if (paletteTimer) {
      clearTimeout(paletteTimer);
    }
    paletteTimer = setTimeout(function () {
      scheduleRender(true);
    }, 20);
  }
}

function onChange(event) {
  var target = event.target;
  if (target.matches('[data-action="switch-workspace"]')) {
    state.selectedWorkspaceId = target.value;
    reloadWorkspace();
    return;
  }
  if (target.name === 'taskFilter') {
    state.taskFilter = target.value;
    scheduleRender();
    return;
  }
  if (target.name === 'apiKey') {
    state.apiKey = target.value;
    localStorage.setItem('synapse-api-key', state.apiKey);
  }
}

async function reloadWorkspace() {
  if (!state.selectedWorkspaceId) {
    scheduleRender();
    return;
  }
  state.loading = true;
  renderApp();
  try {
    await loadWorkspace(state.selectedWorkspaceId);
    state.selectedTaskId = null;
    state.selectedApprovalId = null;
    state.selectedMemoryId = null;
    state.inspectorTab = 'overview';
    state.statusMessage = 'Workspace loaded';
  } catch (error) {
    notify(error.message || 'Could not load workspace.');
  } finally {
    state.loading = false;
    scheduleRender();
  }
}

function onDragStart(event) {
  var target = event.target.closest('[data-task-id]');
  if (target && target.classList.contains('task-card')) {
    event.dataTransfer.effectAllowed = 'move';
    event.dataTransfer.setData('text/plain', target.getAttribute('data-task-id'));
  }
}

function onDragOver(event) {
  var lane = event.target.closest('[data-drop-status]');
  if (lane) {
    event.preventDefault();
    event.dataTransfer.dropEffect = 'move';
  }
}

function onDrop(event) {
  var lane = event.target.closest('[data-drop-status]');
  if (!lane) {
    return;
  }
  var taskId = event.dataTransfer.getData('text/plain');
  if (!taskId) {
    return;
  }
  event.preventDefault();
  updateTask(taskId, lane.getAttribute('data-drop-status'));
}

function onPointerDown(event) {
  var shell = event.target.closest('[data-canvas-shell]');
  if (!shell) {
    return;
  }
  var node = event.target.closest('[data-node-id]');
  if (node) {
    state.selectedNodeId = node.getAttribute('data-node-id');
    state.inspectorTab = 'overview';
    state.canvasDrag = {
      type: 'node',
      nodeId: state.selectedNodeId,
      startX: event.clientX,
      startY: event.clientY,
      initial: getNodePosition(state.selectedNodeId)
    };
    event.preventDefault();
    return;
  }
  state.canvasDrag = {
    type: 'canvas',
    startX: event.clientX,
    startY: event.clientY,
    initialX: state.canvas.x,
    initialY: state.canvas.y
  };
}

function onPointerMove(event) {
  if (!state.canvasDrag) {
    return;
  }
  if (state.canvasDrag.type === 'canvas') {
    var dx = event.clientX - state.canvasDrag.startX;
    var dy = event.clientY - state.canvasDrag.startY;
    state.canvas.x = state.canvasDrag.initialX + dx;
    state.canvas.y = state.canvasDrag.initialY + dy;
    scheduleRender();
    return;
  }
  if (state.canvasDrag.type === 'node') {
    var graph = buildGraph(getSnapshot());
    var deltaX = (event.clientX - state.canvasDrag.startX) / state.canvas.scale;
    var deltaY = (event.clientY - state.canvasDrag.startY) / state.canvas.scale;
    state.nodeLayout[state.canvasDrag.nodeId] = {
      x: state.canvasDrag.initial.x + deltaX,
      y: state.canvasDrag.initial.y + deltaY
    };
    scheduleRender();
    return;
  }
}

function onPointerUp() {
  if (!state.canvasDrag) {
    return;
  }
  if (state.canvasDrag.type === 'node') {
    notify('Visual layout changed locally. Persistence API is not available for graph re-parenting.');
  }
  state.canvasDrag = null;
}

function onWheel(event) {
  var shell = event.target.closest('[data-canvas-shell]');
  if (!shell) {
    return;
  }
  event.preventDefault();
  var delta = event.deltaY > 0 ? -0.08 : 0.08;
  state.canvas.scale = Math.max(0.45, Math.min(1.8, state.canvas.scale + delta));
  scheduleRender();
}

function getNodePosition(nodeId) {
  var graph = buildGraph(getSnapshot());
  var node = graph.nodeMap[nodeId];
  if (!node) {
    return { x: 0, y: 0 };
  }
  return { x: node.x, y: node.y };
}

function fitCanvas() {
  var graph = buildGraph(getSnapshot());
  var minX = graph.nodes.reduce(function (min, node) { return Math.min(min, node.x); }, 0);
  var minY = graph.nodes.reduce(function (min, node) { return Math.min(min, node.y); }, 0);
  var maxX = graph.nodes.reduce(function (max, node) { return Math.max(max, node.x + node.width); }, 0);
  var maxY = graph.nodes.reduce(function (max, node) { return Math.max(max, node.y + node.height); }, 0);
  var width = maxX - minX + 120;
  var height = maxY - minY + 120;
  var scale = Math.min(1.1, 1180 / width, 640 / height);
  state.canvas.scale = Math.max(0.5, Math.min(1.2, scale));
  state.canvas.x = 60 - (minX * state.canvas.scale);
  state.canvas.y = 40 - (minY * state.canvas.scale);
}

function centerSelectedNode() {
  var graph = buildGraph(getSnapshot());
  var selected = graph.nodeMap[state.selectedNodeId];
  if (!selected) {
    fitCanvas();
    return;
  }
  state.canvas.x = 620 - (selected.x + selected.width / 2) * state.canvas.scale;
  state.canvas.y = 280 - (selected.y + selected.height / 2) * state.canvas.scale;
}

function saveSettings(form) {
  if (!form) {
    form = document.querySelector('[data-form="settings"]');
  }
  if (form) {
    state.apiKey = form.querySelector('[name="apiKey"]').value.trim();
    localStorage.setItem('synapse-api-key', state.apiKey);
    notify('Settings saved.');
    return;
  }
  localStorage.setItem('synapse-api-key', state.apiKey);
  notify('Settings saved.');
}

async function createOrganization(form) {
  var payload = {
    name: form.querySelector('[name="name"]').value.trim(),
    totalBudgetUsd: numberOrNull(form.querySelector('[name="budget"]').value)
  };
  await api('/organizations', { method: 'POST', body: payload });
  notify('Organization created.');
  state.createOpen = false;
  await boot();
}

async function createTeam(form) {
  if (!state.selectedWorkspaceId) {
    notify('Select a workspace first.');
    return;
  }
  var payload = {
    name: form.querySelector('[name="name"]').value.trim(),
    teamTopology: form.querySelector('[name="teamTopology"]').value
  };
  await api('/organizations/' + state.selectedWorkspaceId + '/teams', { method: 'POST', body: payload });
  notify('Team created.');
  state.createOpen = false;
  await reloadWorkspace();
}

async function createRole(form) {
  if (!state.selectedWorkspaceId) {
    notify('Select a workspace first.');
    return;
  }
  var responsibilities = String(form.querySelector('[name="responsibilities"]').value || '').split('\n').map(function (line) { return line.trim(); }).filter(Boolean);
  var payload = {
    name: form.querySelector('[name="name"]').value.trim(),
    modelTier: form.querySelector('[name="tier"]').value,
    tokenBudget: numberOrNull(form.querySelector('[name="tokenBudget"]').value),
    systemPrompt: form.querySelector('[name="systemPrompt"]').value,
    responsibilities: responsibilities
  };
  await api('/organizations/' + state.selectedWorkspaceId + '/roles', { method: 'POST', body: payload });
  notify('Role created.');
  state.createOpen = false;
  await reloadWorkspace();
}

async function createGoal(form) {
  if (!state.selectedWorkspaceId) {
    notify('Select a workspace first.');
    return;
  }
  var payload = { description: form.querySelector('[name="description"]').value.trim() };
  await api('/organizations/' + state.selectedWorkspaceId + '/goals', { method: 'POST', body: payload });
  notify('Goal created.');
  state.createOpen = false;
  await reloadWorkspace();
}

async function assignTask(form) {
  var taskId = form.getAttribute('data-task-id');
  var assignedAgentId = form.querySelector('[name="assignedAgentId"]').value;
  var payload = { assignedAgentId: assignedAgentId || null };
  await api('/workspaces/tasks/' + taskId, { method: 'PATCH', body: payload });
  notify('Task updated.');
  await reloadWorkspace();
}

async function updateTask(taskId, status) {
  await api('/workspaces/tasks/' + taskId, { method: 'PATCH', body: { status: status } });
  notify('Task moved to ' + laneLabel(status) + '.');
  await reloadWorkspace();
}

async function resolveApproval(id, decision) {
  await api('/approvals/' + id + '/resolve', { method: 'POST', body: { decision: decision, resolvedBy: 'SYNAPSE Studio' } });
  notify('Approval ' + statusLabelForApproval(decision) + '.');
  await reloadWorkspace();
}

async function explainMemory(id) {
  state.selectedMemoryId = id;
  state.explain = await api('/memory/decisions/' + id + '/explain');
  scheduleRender();
}

async function seedDemo() {
  var response = await api('/demo/seed', { method: 'POST' });
  notify('Seeded ' + (response.organizationName || 'demo workspace') + '.');
  await boot();
}

async function runEvaluation() {
  state.evaluation = await api('/demo/evaluate?runs=50', { method: 'POST' });
  notify('Evaluation complete.');
  scheduleRender();
}

async function copyReport() {
  var report = state.evaluation && state.evaluation.reportMarkdown ? state.evaluation.reportMarkdown : '';
  if (!report) {
    notify('No report available.');
    return;
  }
  await navigator.clipboard.writeText(report);
  notify('Report copied.');
}

function runPaletteItem(item) {
  if (!item || typeof item.action !== 'function') {
    return;
  }
  state.paletteOpen = false;
  item.action();
  scheduleRender();
}

function numberValue(value) {
  if (value == null || value === '') {
    return '0';
  }
  var num = Number(value);
  if (Number.isNaN(num)) {
    return String(value);
  }
  return numberFmt.format(num);
}

function moneyValue(value) {
  if (value == null || value === '') {
    return moneyFmt.format(0);
  }
  var num = Number(value);
  if (Number.isNaN(num)) {
    return String(value);
  }
  return moneyFmt.format(num);
}

function shortId(value) {
  if (!value) {
    return '—';
  }
  var text = String(value);
  return text.length > 8 ? text.slice(0, 8) : text;
}

function previewText(value) {
  if (!value) {
    return '—';
  }
  var text = String(value);
  return text.length > 120 ? text.slice(0, 117) + '…' : text;
}

function formatDate(value) {
  if (!value) {
    return '—';
  }
  var date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return String(value);
  }
  return dateFmt.format(date);
}

function toneForStatus(status) {
  status = String(status || '').toUpperCase();
  if (status === 'ACTIVE' || status === 'IN_PROGRESS' || status === 'COMPLETED' || status === 'APPROVED') return 'good';
  if (status === 'BLOCKED' || status === 'FAILED' || status === 'REJECTED') return 'bad';
  if (status === 'PENDING' || status === 'ASSIGNED' || status === 'WAITING') return 'warn';
  return 'accent';
}

function toneForApproval(status) {
  return toneForStatus(status);
}

function toneForBudget(status) {
  status = String(status || '').toUpperCase();
  if (status === 'HEALTHY') return 'good';
  if (status === 'WARN' || status === 'WARNING') return 'warn';
  if (status === 'EXCEEDED') return 'bad';
  return 'accent';
}

function contains(value, query) {
  return String(value || '').toLowerCase().indexOf(query) >= 0;
}

function numberOrNull(value) {
  if (value == null || value === '') {
    return null;
  }
  var num = Number(value);
  return Number.isNaN(num) ? null : num;
}

function escapeHtml(value) {
  var text = value == null ? '' : String(value);
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

function escapeAttr(value) {
  return escapeHtml(value).replace(/`/g, '&#96;');
}

function option(value, label, selectedValue) {
  return '<option value="' + escapeHtml(value) + '"' + (String(value) === String(selectedValue) ? ' selected' : '') + '>' + escapeHtml(label) + '</option>';
}

function api(path, options) {
  var headers = { 'Content-Type': 'application/json' };
  if (state.apiKey) {
    headers['X-API-Key'] = state.apiKey;
  }
  var request = {
    headers: headers,
    method: (options && options.method) || 'GET'
  };
  if (options && options.body != null) {
    request.body = typeof options.body === 'string' ? options.body : JSON.stringify(options.body);
  }
  return fetch(path, request).then(function (response) {
    if (!response.ok) {
      return response.text().then(function (text) {
        throw new Error(text || ('Request failed: ' + response.status));
      });
    }
    var contentType = response.headers.get('content-type') || '';
    if (contentType.indexOf('application/json') >= 0) {
      return response.json();
    }
    return response.text();
  });
}
