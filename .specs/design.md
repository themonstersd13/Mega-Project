# SYNAPSE — Design Document

Status: Implementation-ready
Companion to: `requirements.md` (what) and `implementation_plan.md` (build order)

---

## 1. Architecture Overview

Single Spring Boot application, four layers, one PostgreSQL database:

```
Controller Layer   REST endpoints (org/role/team/goal/task/memory/approval/predict)
      |
Service Layer      Orchestrator (Decomposer, Matcher, RequestScorer, BatchScheduler,
      |             Dispatcher), MemoryService, GovernanceService, CostModelService
      |
Gateway Layer       LlmProvider (Anthropic) + EmbeddingProvider (Voyage AI),
      |             both wrapped in Retry + CircuitBreaker decorators
      |
Repository Layer    Spring Data JPA repositories over PostgreSQL + pgvector
```

All layers run in one JVM process. Internal components communicate via direct method
calls or `ApplicationEventPublisher` — no network hop, no message broker.

---

## 2. Package Structure (Single Module)

```
com.synapse
├── core/            Organization, Role, Agent, Team, TaskItem, Goal — JPA entities
├── scheduler/        Decomposer, Matcher, RequestScorer, BatchScheduler, Dispatcher
├── memory/            MemoryService, MemoryEntry, MemoryRelation, embedding calls
├── governance/         BudgetTracker, ApprovalGate, EscalationHandler chain,
│                       ConstraintEngine
├── resilience/          ResilientLlmGateway, ResilientEmbeddingGateway, DeadLetterEntry
├── analytics/             ExecutionLogger, SyntheticGoalGenerator, CostModelService,
│                          model training pipeline
├── llm/                    LlmProvider interface, AnthropicProvider, prompt templates,
│                          structured-output DTOs
├── security/                ApiKeyAuthFilter
├── api/                      REST controllers, request/response DTOs
├── config/                    Beans: TaskScheduler, WebClient, Resilience4j config
└── SynapseApplication.java     @SpringBootApplication entry point
```

---

## 3. Domain Model

### 3.1 Entity-Relationship Summary

```
Organization 1───* Team
Team          1───* Team            (self-referencing: parent_team_id)
Team          1───* Agent
Role          1───* Agent
Organization  1───* Goal
Goal          1───* TaskItem
TaskItem      1───1 TaskItem        (self-referencing: parent_task_id)
TaskItem      *───* MemoryEntry     (a task can produce/consume several memories)
MemoryEntry   *───* MemoryEntry     (via MemoryRelation: EVIDENCE, SUPERSEDES, ...)
TaskItem      1───* ExecutionLog
TaskItem      1───* Approval
```

### 3.2 Schema (PostgreSQL, applied via Flyway — see `implementation_plan.md` §5)

```sql
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE organization (
    id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name              VARCHAR(200) NOT NULL,
    total_budget_usd  NUMERIC(10,4) NOT NULL,
    spent_budget_usd  NUMERIC(10,4) NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE role (
    id                   UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id      UUID NOT NULL REFERENCES organization(id),
    name                 VARCHAR(100) NOT NULL,
    system_prompt        TEXT NOT NULL,
    model_tier           VARCHAR(20) NOT NULL,      -- HAIKU | SONNET | OPUS
    token_budget         NUMERIC(10,4) NOT NULL,
    escalates_to_role_id UUID REFERENCES role(id),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE role_responsibility (
    role_id         UUID NOT NULL REFERENCES role(id) ON DELETE CASCADE,
    responsibility  VARCHAR(100) NOT NULL,
    PRIMARY KEY (role_id, responsibility)
);

CREATE TABLE team (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id  UUID NOT NULL REFERENCES organization(id),
    name             VARCHAR(100) NOT NULL,
    topology         VARCHAR(20) NOT NULL,          -- HIERARCHICAL | PEER_TO_PEER | HYBRID
    parent_team_id   UUID REFERENCES team(id),
    lead_agent_id    UUID,                          -- FK added after agent table exists
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE agent (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    role_id         UUID NOT NULL REFERENCES role(id),
    team_id         UUID REFERENCES team(id),
    status          VARCHAR(20) NOT NULL DEFAULT 'IDLE',  -- IDLE|WORKING|BLOCKED|RETIRED
    tokens_used     NUMERIC(10,4) NOT NULL DEFAULT 0,
    last_active_at  TIMESTAMPTZ,
    version         INT NOT NULL DEFAULT 0,               -- optimistic lock
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE team ADD CONSTRAINT fk_team_lead_agent
    FOREIGN KEY (lead_agent_id) REFERENCES agent(id);

CREATE TABLE goal (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id  UUID NOT NULL REFERENCES organization(id),
    description      TEXT NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at     TIMESTAMPTZ
);

CREATE TABLE task_item (
    id                    UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    goal_id               UUID NOT NULL REFERENCES goal(id),
    organization_id       UUID NOT NULL REFERENCES organization(id),
    parent_task_id        UUID REFERENCES task_item(id),
    description           TEXT NOT NULL,
    required_skill_tags   TEXT[] NOT NULL DEFAULT '{}',
    status                VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    assigned_agent_id     UUID REFERENCES agent(id),
    priority              SMALLINT NOT NULL DEFAULT 5,
    deadline              TIMESTAMPTZ,
    estimated_cost_usd    NUMERIC(10,4),
    estimated_latency_ms  BIGINT,
    actual_cost_usd       NUMERIC(10,4),
    actual_latency_ms     BIGINT,
    confidence            SMALLINT,
    retry_count           INT NOT NULL DEFAULT 0,
    version               INT NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE memory_entry (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id  UUID NOT NULL REFERENCES organization(id),
    agent_id         UUID REFERENCES agent(id),
    scope            VARCHAR(20) NOT NULL,   -- PRIVATE | TEAM | ORG
    content          TEXT NOT NULL,
    embedding        vector(1024),           -- dimension must match embedding model
    confidence       SMALLINT,
    superseded       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_memory_embedding ON memory_entry
    USING ivfflat (embedding vector_cosine_ops) WITH (lists = 10);
-- `lists` is set low (10) deliberately for this project's expected data volume.
-- ivfflat's lists parameter is only meaningful once a table holds several thousand
-- rows (rule of thumb: roughly rows / 1000); at that count it would need raising.
-- Below a few thousand rows, Postgres's plain sequential-scan cosine distance is
-- still fast and arguably more accurate than an under-populated ANN index — this
-- index is kept for forward-compatibility, not because it earns its keep yet.

CREATE TABLE memory_relation (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    from_memory_id  UUID NOT NULL REFERENCES memory_entry(id),
    to_memory_id    UUID NOT NULL REFERENCES memory_entry(id),
    relation_type   VARCHAR(30) NOT NULL,  -- EVIDENCE|CONSIDERED_ALTERNATIVE|DEPENDS_ON|SUPERSEDES
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE approval (
    id                 UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    task_id            UUID REFERENCES task_item(id),
    action_description TEXT NOT NULL,
    effect_type        VARCHAR(20) NOT NULL,  -- EXTERNAL | SPEND | DATA
    confidence         SMALLINT,
    status             VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    resolved_by        VARCHAR(100),
    resolved_at        TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE execution_log (
    id                          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    task_id                     UUID NOT NULL REFERENCES task_item(id),
    role_name                   VARCHAR(100) NOT NULL,
    required_skill_tag_count    INT NOT NULL,
    description_length          INT NOT NULL,
    model_tier                  VARCHAR(20) NOT NULL,
    batch_size_at_execution     INT NOT NULL,
    context_overlap_score       NUMERIC(5,4),
    deadline_proximity_minutes  INT,
    priority                    SMALLINT NOT NULL,
    actual_tokens_used          INT NOT NULL,
    actual_cost_usd             NUMERIC(10,4) NOT NULL,
    actual_latency_ms           BIGINT NOT NULL,
    confidence                  SMALLINT NOT NULL,
    was_escalated               BOOLEAN NOT NULL DEFAULT FALSE,
    retry_count                 INT NOT NULL DEFAULT 0,
    succeeded                   BOOLEAN NOT NULL,
    human_approval_required     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE dead_letter_entry (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    task_id          UUID REFERENCES task_item(id),
    original_prompt  TEXT NOT NULL,
    failure_reason   TEXT NOT NULL,
    failed_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved         BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE message (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    run_id              UUID NOT NULL,           -- == goal.id
    trace_id            UUID NOT NULL,
    parent_message_id   UUID REFERENCES message(id),
    sender_agent_id     UUID NOT NULL REFERENCES agent(id),
    recipient_agent_id  UUID REFERENCES agent(id),  -- NULL = broadcast
    content             TEXT NOT NULL,
    confidence          SMALLINT,
    sent_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_message_run_id ON message(run_id);
```

---

## 4. Design Patterns (Each Solving a Real Problem)

| Pattern | Location | Problem it solves |
|---|---|---|
| **Builder** | `Organization.builder()...` | Multi-step, optional-heavy construction of an org with roles/budget |
| **Factory Method** | `AgentFactory.createAgent(Role)` | Matcher creates a new agent from a role template without knowing construction details |
| **Composite** | `OrgUnit` interface implemented by `Agent` and `Team` | A Team's members can be Agents or nested Teams; both must answer "who's in this unit" uniformly |
| **Decorator** | `ResilientLlmGateway` wraps `AnthropicProvider` | Retry and circuit-breaking added without changing the gateway interface or its callers |
| **Strategy** | `SchedulingStrategy` interface, `WeightedSchedulingStrategy` impl | Batch-scoring logic must be swappable/testable independent of the scheduler |
| **Chain of Responsibility** | `EscalationHandler` → `AgentHandler` → `ManagerHandler` → `HumanApprovalHandler` | Escalation is literally "resolve here, else pass up" |
| **Observer** | `TaskCompletedEvent` via `ApplicationEventPublisher` | Budget tracker, execution logger, and health checks all react to task completion independently |
| **State** | `TaskState` interface (`PendingState`, `AssignedState`, ...) | A task's valid next actions depend on its current status; prevents invalid transitions |
| **Command** | `AgentAction` interface, intercepted by `ApprovalGate` | An action must be captured as an object so it can be held, logged, or rejected before executing |

**Justification tier** (for viva defense — a pattern earns its place only if there's a
concrete call site that needs it, not because the list looked incomplete without it):

- **Load-bearing, defend without hesitation**: Strategy (scheduler must be swappable
  and unit-testable in isolation), Factory Method (Matcher creates agents without
  knowing `Agent`'s constructor), Decorator (resilience wrapping is the textbook case),
  Chain of Responsibility (escalation is inherently sequential), Command (an action
  must exist as data before `ApprovalGate` decides whether to run it), Observer
  (three independent listeners react to `TaskCompletedEvent` with zero coupling to
  each other or to the `Dispatcher` that fires it — this is real decoupling, not
  decoration), State (task-status transitions are guarded specifically because
  concurrent, invalid transitions are a real risk under the optimistic-locking model
  in §3.2).
- **Kept, with a concrete justification below**: Composite — see the usage example
  immediately after this list; without a real call site this pattern would be
  decorative, so one is given explicitly rather than assumed.
- **Low-stakes either way**: Builder — idiomatic, near-free with Lombok's `@Builder`,
  but replaceable with a plain constructor + setters without materially changing the
  design; kept for readability, not defended as a load-bearing architectural choice.

Composite's concrete call site — without this, budget rollups for a nested team would
need special-casing for "team contains agents" vs. "team contains sub-teams" at every
call site, which is exactly what Composite exists to avoid:

```java
public interface OrgUnit {
    List<Agent> allAgents();  // flattens recursively regardless of nesting depth
}

public class Agent implements OrgUnit {
    public List<Agent> allAgents() { return List.of(this); }
}

public class Team implements OrgUnit {
    private List<OrgUnit> members;  // each member is an Agent OR a nested Team
    public List<Agent> allAgents() {
        return members.stream().flatMap(m -> m.allAgents().stream()).toList();
    }
}

// Real call site: BudgetTracker needs total spend across an entire team, including
// arbitrarily nested sub-teams, without knowing or caring about the nesting depth.
@Component
public class BudgetTracker {
    public BigDecimal teamSpend(Team team) {
        return team.allAgents().stream()
            .map(Agent::tokensUsed)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
```

Example — the pattern most central to the system, the scored batch scheduler. Note
that scoring happens **after** a compatibility filter, not on the raw pending queue —
grouping by model tier alone (an earlier draft's mistake) does not enforce FR-11
(dependent tasks must never be batched together), nor budget or max-batch-size limits:

```java
public interface BatchCompatibilityChecker {
    boolean areCompatible(PendingRequest a, PendingRequest b);
}

@Component
public class DefaultBatchCompatibilityChecker implements BatchCompatibilityChecker {
    @Value("${synapse.scheduler.max-batch-size}") int maxBatchSize;

    public boolean areCompatible(PendingRequest a, PendingRequest b) {
        return a.modelTier() == b.modelTier()
            && !dependsOn(a, b) && !dependsOn(b, a)          // FR-11
            && a.organizationId().equals(b.organizationId())  // no cross-org batching
            && !bothWouldExceedBudget(a, b);
    }

    private boolean dependsOn(PendingRequest r1, PendingRequest r2) {
        // true if r1's task.parentTaskId == r2's task.id, or r1 explicitly
        // declares a data dependency on r2's not-yet-produced output
        return r1.dependsOnTaskId() != null && r1.dependsOnTaskId().equals(r2.taskId());
    }
}
```

```java
public interface SchedulingStrategy {
    double score(PendingRequest request, BatchContext context);
}

@Component
public class WeightedSchedulingStrategy implements SchedulingStrategy {
    @Value("${synapse.scheduler.weight-priority}")   double wPriority;
    @Value("${synapse.scheduler.weight-deadline}")    double wDeadline;
    @Value("${synapse.scheduler.weight-cost}")        double wCost;
    @Value("${synapse.scheduler.weight-confidence}")  double wConfidence;
    @Value("${synapse.scheduler.weight-overlap}")     double wOverlap;

    // All five components are normalized to [0,1] before weighting so no single
    // unbounded term (e.g. a very cheap task) can dominate the sum regardless of the
    // other signals. Cost is treated as a *reward for cheaper tasks* — explicit
    // design choice, not an accidental side effect of the formula — normalized
    // against the min/max estimated cost of the *current candidate pool*, not a
    // global constant, so its influence stays proportionate batch to batch.
    public double score(PendingRequest r, BatchContext ctx) {
        double priorityScore   = r.priority() / 10.0;                              // [0,1]
        double deadlineScore   = normalizeDeadline(r.deadline());                  // [0,1]
        double costScore       = 1.0 - minMaxNormalize(r.estimatedCostUsd(),
                                        ctx.minCostInPool(), ctx.maxCostInPool());  // [0,1], cheaper => higher
        double confidenceScore = r.requiredConfidence() / 100.0;                   // [0,1]
        double overlapScore    = ContextOverlap.jaccard(r.contextTags(),
                                        ctx.currentBatchTags());                    // [0,1]

        return wPriority * priorityScore
             + wDeadline * deadlineScore
             + wCost * costScore
             + wConfidence * confidenceScore
             + wOverlap * overlapScore;
    }
}
```

```java
@Component
@RequiredArgsConstructor
public class BatchScheduler {
    private final Queue<PendingRequest> pending = new ConcurrentLinkedQueue<>();
    private final BatchCompatibilityChecker compatibilityChecker;
    private final SchedulingStrategy strategy;
    private final ResilientLlmGateway llmGateway;
    private final DispatcherService dispatcher;

    public void enqueue(PendingRequest r) { pending.add(r); }

    @Scheduled(fixedDelayString = "${synapse.batch-window-ms}")
    public void flush() {
        List<PendingRequest> candidates = drainAll(pending);
        if (candidates.isEmpty()) return;

        candidates.stream()
            .collect(Collectors.groupingBy(PendingRequest::modelTier))
            .forEach((tier, tierGroup) -> {
                for (List<PendingRequest> batch : buildCompatibleBatches(tierGroup)) {
                    List<PendingRequest> ranked = batch.stream()
                        .sorted(Comparator.comparingDouble(
                            r -> -strategy.score(r, BatchContext.of(batch))))
                        .toList();
                    String prompt = PromptMultiplexer.build(ranked);
                    AgentBatchResponse resp = llmGateway.callStructured(
                        tier, prompt, AgentBatchResponse.class);
                    dispatcher.distribute(resp, ranked);
                }
            });
    }

    // Greedily groups a tier's candidates into one or more compatible batches,
    // respecting compatibilityChecker (FR-11, budget, org boundary) and
    // synapse.scheduler.max-batch-size. A request incompatible with every open
    // batch starts a new one rather than being silently dropped.
    private List<List<PendingRequest>> buildCompatibleBatches(List<PendingRequest> group) {
        List<List<PendingRequest>> batches = new ArrayList<>();
        for (PendingRequest r : group) {
            List<PendingRequest> target = batches.stream()
                .filter(b -> b.size() < maxBatchSize
                          && b.stream().allMatch(existing -> compatibilityChecker.areCompatible(r, existing)))
                .findFirst()
                .orElseGet(() -> { List<PendingRequest> nb = new ArrayList<>(); batches.add(nb); return nb; });
            target.add(r);
        }
        return batches;
    }
}
```

**Concurrency guarantees**: `@Scheduled` runs on a dedicated `TaskScheduler` bean with a
fixed thread pool (size 2 — one for batch flush, one for the health-sweep job; see
`config/SchedulingConfig.java`). `Agent` and `TaskItem` both carry a `@Version` column
for optimistic locking, so two concurrent assignment attempts on the same agent cannot
silently overwrite each other — the losing transaction retries against the next
available idle agent.

**Scheduler configuration** (`application.yml` — all five score weights plus the
compatibility/batching limits referenced above, gathered in one place):

```yaml
synapse:
  batch-window-ms: 300
  scheduler:
    max-batch-size: 8
    weight-priority: 0.30
    weight-deadline: 0.20
    weight-cost: 0.15
    weight-confidence: 0.15
    weight-overlap: 0.20
```

---

## 5. LLM Integration Design

### 5.1 Model Tier Mapping

Model **strings are never hardcoded** in business logic — only the tier enum
(`HAIKU` / `SONNET` / `OPUS`) is. The actual model string per tier is configured in
`application.yml`, so it can be updated as Anthropic's catalog changes without a code
change:

```yaml
synapse:
  llm:
    model-haiku: claude-haiku-4-5-20251001
    model-sonnet: claude-sonnet-5
    model-opus: claude-opus-4-8
```

> **Verify before running**: confirm these three model strings are still current in
> Anthropic's documentation at build time — model identifiers are updated over time and
> should not be trusted from any document, including this one, without a live check.

### 5.2 Structured Output Contract

```java
public record AgentSlice(
    String agentId, String response, int confidence, String suggestedNextAction) {}

public record AgentBatchResponse(Map<String, AgentSlice> results) {}
```

The system prompt for a batched call instructs the model to return **only** a JSON
object matching this shape, keyed by `agentId`. Parsing uses Jackson with
`FAIL_ON_UNKNOWN_PROPERTIES` disabled (forward-compatible) and
`FAIL_ON_MISSING_CREATABLE_PROPERTIES` enabled (catches truncated/malformed output
immediately rather than silently defaulting fields).

### 5.3 Prompt Template Shape

```
[SHARED CONTEXT — organization name, goal summary, current date]

--- AGENT abc123 (Role: Researcher) ---
System prompt: <role.systemPrompt>
Task: <task.description>
Relevant memory: <top-3 recalled MemoryEntry summaries>

--- AGENT def456 (Role: Writer) ---
...

Respond ONLY with JSON matching: {"results": {"<agentId>": {"response": "...",
"confidence": 0-100, "suggestedNextAction": "..."}}}
```

### 5.4 Embeddings — Decision and Rationale

Anthropic's API does not provide a native embeddings endpoint. Two options were
considered:

| Option | Trade-off |
|---|---|
| **Voyage AI API** (chosen) | One more outbound HTTPS call, same pattern as the LLM gateway; no new infrastructure to run; requires a second API key. |
| Local embedding model (DJL + ONNX) | Zero external dependency after first model download, but adds native-library setup risk and a new failure class unrelated to the rest of the stack. |

Voyage AI is used as the default because it keeps the "single process, no extra
services" principle intact — it is architecturally identical to the Anthropic call,
just a different vendor. `EmbeddingProvider` is defined as an interface specifically so
a local-model implementation could be substituted later without touching
`MemoryService`.

```java
public interface EmbeddingProvider {
    float[] embed(String text);
}

@Component
public class VoyageEmbeddingProvider implements EmbeddingProvider {
    // POST https://api.voyageai.com/v1/embeddings, model "voyage-3", 1024 dims
}
```

---

## 6. Memory and Explainability Design

```java
public interface MemoryService {
    UUID store(String content, MemoryScope scope, UUID agentId);
    List<MemoryMatch> recall(String query, MemoryScope scope, int topK);
    void linkRelation(UUID fromId, UUID toId, RelationType type);
    ExplanationTrace explainWhy(UUID memoryId);
}
```

`recall` embeds the query via `EmbeddingProvider`, then runs a cosine-similarity search
using the `ivfflat` index on `memory_entry.embedding`.

**Relation direction convention (stated explicitly, since the recursive query below is
only correct if this is followed consistently everywhere a relation is written):**

| Relation type | `from_memory_id` | `to_memory_id` |
|---|---|---|
| `EVIDENCE` | the supporting/source memory | the decision it supports |
| `CONSIDERED_ALTERNATIVE` | the rejected alternative | the decision it was rejected in favor of |
| `DEPENDS_ON` | the dependent memory | the memory it depends on |
| `SUPERSEDES` | the new decision | the older decision it replaces |

In short: **`from` always points at the more "upstream" or "source" node, `to` always
points at the decision/target it relates to.** `explainWhy(decisionId)` therefore looks
up rows where `to_memory_id = decisionId` and returns their `from_memory_id` side:

```sql
WITH RECURSIVE trace AS (
    SELECT id, content, 0 AS depth FROM memory_entry WHERE id = :memoryId
    UNION ALL
    -- m = the "from" side (evidence / alternative / superseding decision);
    -- t = the "to" side already in the trace (the decision being explained).
    SELECT m.id, m.content, t.depth + 1
    FROM memory_entry m
    JOIN memory_relation r ON m.id = r.from_memory_id
    JOIN trace t ON r.to_memory_id = t.id
    WHERE t.depth < 5
)
SELECT * FROM trace ORDER BY depth;
```

---

## 7. Governance Design

### 7.1 Budget Accounting — Single Source of Truth

`organization.spent_budget_usd` and `agent.tokens_used` are **cached aggregates, not
the source of truth** — `execution_log` is the authoritative, append-only record (this
was left implicit in an earlier draft; stated explicitly here because it matters under
concurrency). Both counters are updated in the **same transaction** as the
`execution_log` insert, never as a separate eventual-consistency step:

```java
@Component
@RequiredArgsConstructor
public class ExecutionLogger {
    @Transactional
    public void recordCompletion(TaskItem task, ExecutionOutcome outcome) {
        executionLogRepository.save(ExecutionLog.from(task, outcome));      // source of truth
        agentRepository.incrementTokensUsed(task.getAssignedAgentId(), outcome.tokensUsed());
        organizationRepository.incrementSpend(task.getOrganizationId(), outcome.costUsd());
    }
}
```

If the two ever disagree (e.g. after a manual DB fix, or a bug), `organization
.spent_budget_usd` and `agent.tokens_used` should be treated as wrong and recomputed
via `SELECT SUM(actual_cost_usd) FROM execution_log WHERE ...` — never the reverse.

### 7.2 Approval Gate and Escalation

```java
@Component
@RequiredArgsConstructor
public class ApprovalGate {
    public void intercept(AgentAction action) {
        boolean needsApproval = action.effectType() == EffectType.EXTERNAL
            || action.effectType() == EffectType.SPEND
            || action.confidence() < confidenceThreshold;
        if (needsApproval) {
            approvalRepository.save(Approval.pendingFrom(action));
            action.markAwaitingApproval();
        } else {
            action.execute();
        }
    }
}
```

Escalation chain (Chain of Responsibility):

```java
public abstract class EscalationHandler {
    protected EscalationHandler next;
    public EscalationHandler linkNext(EscalationHandler n) { this.next = n; return n; }
    public abstract boolean handle(Escalation e);
}

@Component
public class AgentHandler extends EscalationHandler {
    public boolean handle(Escalation e) {
        return canResolveAtAgentLevel(e) || (next != null && next.handle(e));
    }
}
// ManagerHandler mirrors AgentHandler; HumanApprovalHandler is terminal —
// always creates an Approval row and returns true (resolved by definition of "escalated to human").
```

`ConstraintEngine` enforces topology routing (FR-21) by checking, before a `Message` is
persisted, whether sender and recipient share a team or whether the sender is that
team's lead; non-compliant messages are rejected with a routing error rather than
silently delivered.

---

## 8. Resilience Design

Resilience4j annotations wrap both external gateways identically:

```java
@Component
public class ResilientLlmGateway implements LlmGateway {
    @Retry(name = "llmGateway", fallbackMethod = "fallbackTier")
    @CircuitBreaker(name = "llmGateway")
    public <T> T callStructured(ModelTier tier, String prompt, Class<T> type) {
        return delegate.callStructured(tier, prompt, type);
    }

    private <T> T fallbackTier(ModelTier tier, String prompt, Class<T> type, Throwable ex) {
        if (tier != ModelTier.HAIKU) {
            return delegate.callStructured(ModelTier.HAIKU, prompt, type);
        }
        deadLetterRepository.save(DeadLetterEntry.from(prompt, tier, ex));
        throw new UnrecoverableGatewayException(ex);
    }
}
```

```yaml
resilience4j:
  retry:
    instances:
      llmGateway: { maxAttempts: 3, waitDuration: 500ms, exponentialBackoffMultiplier: 2 }
      embeddingGateway: { maxAttempts: 3, waitDuration: 300ms }
  circuitbreaker:
    instances:
      llmGateway: { failureRateThreshold: 50, waitDurationInOpenState: 20s, slidingWindowSize: 20 }
```

---

## 9. REST API Contract

Base path: `/api/v1` — every path below is relative to it (e.g. `/organizations` in
this table means `POST /api/v1/organizations`; the same base path is used everywhere
in `implementation_plan.md`'s demo script and benchmark harness, so there is one
canonical convention across all three documents, not a per-document one).

All state-changing requests require header `X-API-Key: <key>`.

| Endpoint | Method | Body / Params | Response |
|---|---|---|---|
| `/organizations` | POST | `{name, totalBudgetUsd}` | `201` + Organization |
| `/organizations/{id}/roles` | POST | `{name, systemPrompt, modelTier, tokenBudget, responsibilities[]}` | `201` + Role |
| `/organizations/{id}/teams` | POST | `{name, topology, parentTeamId?}` | `201` + Team |
| `/organizations/{id}/goals` | POST | `{description}` | `202` + `{goalId, status: "SUBMITTED"}` |
| `/organizations/{id}/goals/{goalId}/estimate` | GET | — | `{estimatedCostUsd, estimatedLatencyMs, taskCount}` |
| `/tasks/{id}` | GET | — | Task with status, confidence, assignedAgentId |
| `/memory/{agentId}/recall` | GET | `?query=&topK=5` | `[{content, similarity}]` |
| `/memory/decisions/{id}/explain` | GET | — | `{decision, evidence[], rejectedAlternatives[]}` |
| `/approvals` | GET | `?status=PENDING` | Paginated list |
| `/approvals/{id}/resolve` | POST | `{decision: "APPROVE"|"REJECT", resolvedBy}` | `200` + updated Approval |
| `/predict/cost` | POST | `{roleId, description, priority, deadline?}` | `{estimatedCostUsd, estimatedLatencyMs, modelUsed}` |
| `/metrics/runs/{goalId}` | GET | — | `{apiCallCount, inputTokens, outputTokens, totalTokens, totalCostUsd, meanLatencyMs, p95LatencyMs, batchFillRatio, failedTaskCount, recoveredTaskCount}` |
| `/demo/seed` | POST | — | Seeds the AI Product Team demo org (dev/demo profile only) |

**Why `inputTokens`/`outputTokens` are tracked separately, not just `totalCostUsd`:**
batching reduces the *number of API calls* by construction, but it does **not**
automatically reduce *token cost* proportionally — a multiplexed prompt still contains
every agent's own context and instructions, plus the shared context once instead of
per-call. What batching actually eliminates is the repeated per-call overhead (system
prompt, tool schema, network round trip), not each agent's own payload. The evaluation
must report actual input/output tokens against the naive baseline, not infer a cost
reduction from a lower call count alone — otherwise the central claim isn't actually
verified, just assumed.

**Error response shape** (all 4xx/5xx):

```json
{
  "timestamp": "2026-08-19T10:00:00Z",
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "totalBudgetUsd must be positive",
  "path": "/api/v1/organizations"
}
```

Produced by a single `@RestControllerAdvice` — no endpoint hand-rolls its own error
format.

---

## 10. Security Design

Minimal, intentional scope (full RBAC is explicitly out of scope — see
`requirements.md` §9):

```java
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {
    @Value("${synapse.security.api-key}") String expectedKey;

    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                      FilterChain chain) throws IOException, ServletException {
        if (isPublicPath(req) || expectedKey.equals(req.getHeader("X-API-Key"))) {
            chain.doFilter(req, res);
        } else {
            res.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }
}
```

`GET /actuator/health` is the only public path — used for container/orchestrator
health checks and must not require the key.

---

## 11. Cost/Latency Prediction Design

```java
public interface CostLatencyModel {
    Prediction predict(TaskFeatures features);
    ModelMetrics evaluate(List<ExecutionLog> heldOutSet);
}
```

**Feature availability problem, and the fix.** An earlier version of this design used
one feature set for both live prediction and evaluation, including
`batch_size_at_execution` and `context_overlap_score`. That's a real bug, not a style
choice: `POST /predict/cost` (FR-25) is called **before** a task has been scheduled, so
neither feature exists yet at prediction time — the model would be trained on inputs it
can never actually receive in production. `ExecutionLog` therefore feeds two distinct
feature views:

| View | Features | Used by |
|---|---|---|
| **Pre-execution** (available at `/predict/cost` time) | `role_name`, `required_skill_tag_count`, `description_length`, `model_tier`, `deadline_proximity_minutes`, `priority` | The three live `CostLatencyModel` implementations below |
| **Post-execution** (only known after the task actually ran) | everything in Pre-execution, plus `batch_size_at_execution`, `context_overlap_score`, `retry_count`, `was_escalated` | Evaluation/reporting only (§9's `/metrics/runs/{goalId}`, the benchmark harness) — never fed to a live prediction call |

**What is actually being predicted.** Because the live model only sees pre-execution
features, its output is best understood as *"expected cost/latency for this task given
typical current scheduling behavior for its role and tier,"* not some scheduling-policy-
independent "intrinsic" cost. This distinction matters for the report/viva: the number
is a scheduling-aware estimate, and would need retraining (or at minimum re-evaluation)
if the scheduling policy itself changed materially.

Three implementations, all trained on the **pre-execution** feature view →
targets `actual_cost_usd`, `actual_latency_ms`:

1. `HistoricalAverageModel` — mean actual cost/latency per `role_name`; used as the
   cold-start fallback (FR-25) and as the evaluation baseline.
2. `LinearRegressionCostModel` — via Tribuo's `LinearSGDTrainer`.
3. `TreeRegressionCostModel` — via Tribuo's CART regression tree trainer.

Training is triggered by a scheduled job (`@Scheduled(cron = "0 0 3 * * *")`, nightly)
once `execution_log` has at least `synapse.ml.min-training-rows` (default 200) rows;
below that threshold `CostModelService` always serves `HistoricalAverageModel`.
Evaluation uses an 80/20 chronological split (not random) so the test set always
represents more recent behavior than the training set.

---

## 12. Observability

- Every `Message` row carries `run_id` (= `goal.id`) and `trace_id`, so a full
  execution can be reconstructed with `SELECT * FROM message WHERE run_id = ? ORDER BY
  sent_at`.
- Spring Boot Actuator exposes `/actuator/health` and `/actuator/metrics` for basic
  liveness and JVM metrics — no custom dashboard is required for the system to be
  operable, though `implementation_plan.md` includes an optional dashboard phase.
- All service-layer classes log at `INFO` for state transitions (task assigned, batch
  flushed, action held for approval) and `WARN` for degraded paths (retry, fallback
  tier, circuit open) via SLF4J — no logging framework beyond what Spring Boot ships
  with by default.
