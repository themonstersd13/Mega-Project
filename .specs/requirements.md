# SYNAPSE — Requirements Document

Status: Implementation-ready
Scope: Single Spring Boot application, single PostgreSQL database (pgvector extension)

---

## 1. Overview

SYNAPSE is a Spring Boot service that lets a developer define a small organization of
LLM agents (Roles, Agents, Teams), submit a natural-language Goal, and have the system
decompose, schedule, execute, remember, and govern the resulting work — batching
independent agent requests into shared model calls, recording the reasoning behind
significant decisions, and holding costly or externally-visible actions for human
approval.

This document specifies **what** must be built. See `design.md` for **how** it is built,
and `implementation_plan.md` for the **order** in which it is built.

---

## 2. Goals

- G1. Reduce API call count, cost, and latency versus a naive one-call-per-agent
  baseline, on an identical task set, via request batching.
- G2. Make agent decisions explainable after the fact — not just "what happened" but
  "why", including rejected alternatives.
- G3. Prevent any costly or externally-visible action from executing without either
  sufficient confidence or explicit human approval.
- G4. Predict the cost and latency of a task before it runs, using a model trained on
  the system's own execution history, improving over a naive historical average as
  data accumulates.
- G5. Run as a single deployable unit with a single database — no additional services
  to operate.

## 3. Non-Goals (see §9 for the full out-of-scope list)

- Multi-tenant isolation across separate organizations.
- Enterprise RBAC, secret rotation, encryption at rest.
- Online/adaptive learning of scheduling weights during live operation.
- A distributable SDK/package on a public registry.

---

## 4. Actors

| Actor | Description |
|---|---|
| **Organization Admin** | Defines roles, teams, and budgets; submits goals; resolves pending approvals via the API. |
| **Agent** (system-internal) | Executes tasks assigned to it; reports a confidence score; escalates when blocked or low-confidence. |
| **System Health Process** | Scheduled internal process; compacts memory, detects stuck agents and orphaned tasks. |
| **External LLM Provider** | Anthropic API — executes the actual language-model calls. |
| **External Embeddings Provider** | Voyage AI — produces vector embeddings for semantic memory search (see `design.md` §5.4 for rationale). |

---

## 5. Functional Requirements

### 5.1 Organization Management

- **FR-1**: The system shall allow creation of an `Organization` with a name and a
  total token/cost budget.
- **FR-2**: The system shall allow creation of a `Role` with a name, system prompt,
  minimum model tier, token budget, a set of responsibility tags, and an optional
  escalation target role.
- **FR-3**: The system shall allow creation of an `Agent` instance bound to exactly one
  `Role`.
- **FR-4**: The system shall allow grouping `Agent`s into a `Team`, and `Team`s into
  nested sub-Teams, each with a declared topology (`HIERARCHICAL`, `PEER_TO_PEER`, or
  `HYBRID`).
- **FR-5**: The system shall allow submission of a `Goal` (free-text) to an
  `Organization`.

### 5.2 Task Scheduling

- **FR-6**: The system shall decompose a submitted `Goal` into one or more `TaskItem`s,
  each tagged with required skills, a priority (1–10), and an optional deadline.
- **FR-7**: The system shall assign each `TaskItem` to an `IDLE` `Agent` whose `Role`
  responsibility tags match the required skills, preferring the least-loaded matching
  agent; if none is idle, the system shall instantiate a new `Agent` from the matching
  `Role` template.
- **FR-8**: The system shall score pending, independent, same-model-tier `TaskItem`
  requests using a weighted function of priority, deadline proximity, estimated cost,
  required confidence, and context overlap, and shall batch compatible requests into a
  single LLM call within a configurable time window.
- **FR-9**: The system shall parse a batched LLM response into per-agent results using
  a strict, schema-validated JSON contract, and shall deliver each result only to its
  owning `Agent`.
- **FR-10**: On a malformed or missing result for one agent within a batch, the system
  shall retry that agent's request in isolation (non-batched) without discarding or
  re-running the rest of the batch.
- **FR-11**: Two `TaskItem`s shall never be batched together if either has a
  cross-dependency on the other's not-yet-produced output.

### 5.3 Memory and Explainability

- **FR-12**: The system shall store a `MemoryEntry` (with a vector embedding) for
  significant agent outputs, scoped as `PRIVATE` (one agent), `TEAM`, or `ORG`.
- **FR-13**: The system shall support semantic recall: given a query, return the
  top-K most relevant `MemoryEntry` records within a given scope.
- **FR-14**: The system shall support recording relationships between memory entries:
  `EVIDENCE`, `CONSIDERED_ALTERNATIVE`, `DEPENDS_ON`, and `SUPERSEDES`.
- **FR-15**: The system shall support an "explain why" query that, given a decision's
  memory entry ID, returns the full chain of evidence and rejected alternatives behind
  it via graph traversal over the relation table.
- **FR-16**: When a new decision supersedes an older one, the system shall mark the
  older entry as superseded rather than deleting it.

### 5.4 Governance

- **FR-17**: The system shall track cumulative token spend per `Agent` and per
  `Organization` against configured budgets, and shall trigger a graceful
  summarize-and-handoff instruction to an agent at 80% of its budget.
- **FR-18**: The system shall block execution of any action whose `effectType` is
  `EXTERNAL` or `SPEND` (see `design.md` §3.2's `Approval.effect_type` enum), or any
  action with confidence below a configurable threshold
  (default 70), pending human approval.
- **FR-19**: A human reviewer shall be able to approve or reject a pending action via a
  REST endpoint; a rejected action shall not execute.
- **FR-20**: The system shall escalate an unresolved or low-confidence `TaskItem` from
  `Agent` → `Manager` (Team lead) → `Human`, in that order, stopping at the first level
  that resolves it.
- **FR-21**: The system shall enforce topology-based routing constraints — e.g., in a
  `HIERARCHICAL` team, a non-lead agent may not message a member of a different team
  directly and must route through its own team lead.

### 5.5 Intelligence & Analytics

- **FR-22**: The system shall log a structured `ExecutionLog` record for every
  completed `TaskItem`, starting from the first task ever executed in the system —
  logging shall not be deferred to a later development phase.
- **FR-23**: The system shall provide a synthetic workload generator producing
  parameterized, reproducible `Goal`s (varying subtask count, skill diversity, and
  deadline tightness) for testing and for accumulating training data.
- **FR-24**: The system shall train and compare at least three cost/latency
  estimators — a historical-average baseline, a linear regression model, and a
  tree-based regression model — on logged `ExecutionLog` data.
- **FR-25**: The system shall expose a prediction endpoint that returns an estimated
  cost and latency for a not-yet-run `TaskItem`, using the best-performing trained
  model, falling back to the historical average when fewer than a configurable minimum
  number of relevant logged executions exist.
- **FR-26**: The system shall report MAE, RMSE, and R² for each trained model against
  a held-out test split.

### 5.6 Resilience

- **FR-27**: The system shall retry a failed LLM call with exponential backoff before
  falling back to a lower (cheaper/more available) model tier.
- **FR-28**: The system shall open a circuit breaker and pause new batch dispatch if
  the LLM call failure rate crosses a configurable threshold within a rolling window.
- **FR-29**: A request that fails after all retries and fallback attempts shall be
  written to a dead-letter table with its failure reason, rather than retried
  indefinitely or silently dropped.

### 5.7 API and Access

- **FR-30**: All state-changing REST endpoints shall require a valid API key supplied
  via the `X-API-Key` header; requests without a valid key shall be rejected with
  `401 Unauthorized`.
- **FR-31**: All list endpoints shall support pagination via `page` and `size` query
  parameters.

---

## 6. Non-Functional Requirements

| ID | Requirement |
|---|---|
| NFR-1 | Cost per completed task under batching shall be measurably lower than the naive baseline on an identical task set (target: report the actual % reduction, no fixed target claimed in advance). |
| NFR-2 | The system shall run as a single Spring Boot application against a single PostgreSQL database (pgvector extension) — no additional deployed services beyond the two external APIs (LLM, embeddings). |
| NFR-3 | Every cross-cutting concern (retry, circuit breaking, approval interception, escalation) shall be implemented so it can be extended or swapped without modifying the classes it wraps. |
| NFR-4 | The cost/latency prediction model's accuracy shall be evaluated on a held-out split, never solely on training data. |
| NFR-5 | The system shall remain able to accept new goals if the LLM provider is temporarily unavailable, degrading via retry/fallback rather than failing the whole application. |
| NFR-6 | All secrets (API keys, DB credentials) shall be supplied via environment variables — never hardcoded or committed to source control. |
| NFR-7 | Database schema changes shall be applied via versioned migrations (Flyway), not automatic DDL generation, so the schema history is reproducible. |
| NFR-8 | Concurrent task assignment and batch scheduling shall not corrupt agent or task state under concurrent access (enforced via optimistic locking). |

---

## 7. External Dependencies

| Dependency | Purpose | Required credential |
|---|---|---|
| Anthropic API | LLM inference for all agent reasoning | `ANTHROPIC_API_KEY` |
| Voyage AI API | Text embeddings for pgvector semantic search (see `design.md` §5.4) | `VOYAGE_API_KEY` |
| PostgreSQL 15+ with `pgvector` extension | Primary data store, including vector search | Local/managed instance |

No other external services are required. There is no separate ML microservice, no
message broker, and no graph database — see `design.md` for how each of those concerns
is instead handled inside the single application.

---

## 8. Acceptance Criteria

The project is considered functionally complete when all of the following hold:

1. An `Organization` with at least three `Role`s (Researcher, Writer, Reviewer) can be
   created and a `Goal` submitted end-to-end to a completed result via the REST API.
2. Running an identical multi-agent task set through the batched scheduler and through
   a naive one-call-per-agent code path produces a measurable, reported difference in
   API call count, cost, and latency.
3. A memory `explainWhy` query against a real decision made during the demo scenario
   returns its supporting evidence and at least one rejected alternative.
4. At least one action in the demo scenario is correctly intercepted and held for
   human approval, and the demo shows both an approval and a rejection path.
5. All three cost/latency estimators (baseline, linear, tree-based) are trained on
   real logged data and their MAE/RMSE/R² are reported side by side.
6. A simulated LLM timeout is shown to trigger retry → fallback tier → (if exhausted)
   dead-letter logging, without crashing the application or losing the rest of an
   in-flight batch.
7. The entire application starts from `docker-compose up` (database) plus one Spring
   Boot process — no other infrastructure.

---

## 9. Out of Scope

| Not Building | Rationale |
|---|---|
| Multi-tenant isolation across separate organizations | Real production concern, not needed to demonstrate the core contributions |
| Enterprise RBAC, secret rotation, encryption at rest | Out of proportion to project timeline; minimal API-key auth (FR-30) is the deliberate substitute |
| A dedicated graph database (Neo4j) | Relationships modeled in PostgreSQL via `memory_relation` + recursive CTE (see `design.md`) |
| A separate Python ML microservice | Cost/latency models trained and served in-process via a Java ML library |
| Online/adaptive learning of scheduling weights | A distinct research problem (bandit-style learning); named as future work |
| Publishing as a distributable SDK/library | Packaging concern, not an architectural or research contribution |
| Multiple LLM provider implementations (OpenAI, Gemini, local) | An `LlmProvider` interface exists for extensibility; only Anthropic is implemented |
