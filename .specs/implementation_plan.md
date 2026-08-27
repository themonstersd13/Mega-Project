# SYNAPSE — Implementation Plan

Status: Implementation-ready
Companion to: `requirements.md` (what) and `design.md` (how)

---

## 1. Prerequisites

| Tool | Version | Purpose |
|---|---|---|
| JDK | 21 (LTS) | Virtual threads help with I/O-bound LLM/embedding calls |
| Maven | 3.9+ | Build tool (single module — no multi-module complexity) |
| Docker + Docker Compose | Recent stable | Local PostgreSQL + pgvector |
| An Anthropic API key | — | LLM inference |
| A Voyage AI API key | — | Embeddings (see `design.md` §5.4) |

Run `java -version`, `mvn -version`, and `docker --version` before starting Phase 1 to
confirm the environment is ready.

---

## 2. Project Scaffolding

Single Maven module, standard layout:

```
synapse/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/com/synapse/...        (see design.md §2 for package structure)
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       └── db/migration/            (Flyway SQL files)
│   └── test/
│       └── java/com/synapse/...
├── docker-compose.yml
└── README.md
```

Key `pom.xml` dependencies:

```xml
<dependencies>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-actuator</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-webflux</artifactId></dependency> <!-- WebClient only -->
    <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId></dependency>
    <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-database-postgresql</artifactId></dependency>
    <dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId></dependency>
    <dependency><groupId>com.pgvector</groupId><artifactId>pgvector</artifactId><version>0.1.6</version></dependency>
    <dependency><groupId>io.github.resilience4j</groupId><artifactId>resilience4j-spring-boot3</artifactId><version>2.2.0</version></dependency>
    <dependency><groupId>org.tribuo</groupId><artifactId>tribuo-regression-sgd</artifactId><version>4.3.1</version></dependency>
    <dependency><groupId>org.tribuo</groupId><artifactId>tribuo-regression-tree</artifactId><version>4.3.1</version></dependency>
    <dependency><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId><optional>true</optional></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
    <dependency><groupId>org.testcontainers</groupId><artifactId>postgresql</artifactId><scope>test</scope></dependency>
    <dependency><groupId>org.testcontainers</groupId><artifactId>junit-jupiter</artifactId><scope>test</scope></dependency>
</dependencies>
```

---

## 3. Environment Variables

Never committed to source control — supplied via `.env` (local, gitignored) or the
deployment platform's secret manager:

```
ANTHROPIC_API_KEY=sk-ant-...
VOYAGE_API_KEY=pa-...
SYNAPSE_API_KEY=<any-strong-random-string>       # your own API's auth key, FR-30
POSTGRES_DB=synapse
POSTGRES_USER=synapse
POSTGRES_PASSWORD=<strong-local-password>
SPRING_PROFILES_ACTIVE=dev
```

`application.yml` references these via `${ANTHROPIC_API_KEY}` etc. — no key is ever
hardcoded.

---

## 4. Local Dev Stack

```yaml
# docker-compose.yml
services:
  postgres:
    image: pgvector/pgvector:pg16
    environment:
      POSTGRES_DB: ${POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    ports: ["5432:5432"]
    volumes: ["synapse_pgdata:/var/lib/postgresql/data"]
volumes:
  synapse_pgdata:
```

Run: `docker compose up -d` then `mvn spring-boot:run`. That's the entire local
environment — matches NFR-2 and the "single database, no extra infrastructure"
constraint directly; using the `pgvector/pgvector` image means the extension is
pre-installed, no manual `CREATE EXTENSION` step is needed outside migrations.

---

## 5. Database Migrations (Flyway)

Migration files live in `src/main/resources/db/migration/`, applied automatically on
startup (`spring.flyway.enabled=true`, the default once the dependency is present):

| File | Contents |
|---|---|
| `V1__init_extensions.sql` | `CREATE EXTENSION vector`, `CREATE EXTENSION "uuid-ossp"` |
| `V2__core_org_role_team_agent.sql` | `organization`, `role`, `role_responsibility`, `team`, `agent` tables (design.md §3.2) |
| `V3__goal_task.sql` | `goal`, `task_item` |
| `V4__memory.sql` | `memory_entry`, `memory_relation`, the `ivfflat` index |
| `V5__governance.sql` | `approval` |
| `V6__analytics_resilience.sql` | `execution_log`, `dead_letter_entry` |
| `V7__messaging.sql` | `message` |

`spring.jpa.hibernate.ddl-auto` is set to `validate`, **never** `update` — Hibernate
checks the schema matches the entities but Flyway alone owns schema changes. This is a
deliberate departure from earlier draft designs that used `ddl-auto: update`, corrected
here because a real working app needs a reproducible, versioned schema history (NFR-7).

---

## 6. Phase-by-Phase Build Plan

Each phase lists its **Definition of Done** — the build does not proceed to the next
phase until these are true.

### Phase 0 — Scaffolding (2–3 days)

- Maven project builds and starts (`mvn spring-boot:run`) against the Dockerized
  Postgres, with `V1`–`V2` migrations applied successfully.
- `ApiKeyAuthFilter` in place; an unauthenticated request to any non-health endpoint
  returns `401`.
- `/actuator/health` returns `200` without a key.

**DoD**: `curl http://localhost:8080/actuator/health` returns `{"status":"UP"}`.

### Phase 1 — Core Domain + Single-Agent Flow (Week 1–3)

- `Organization`, `Role`, `Team`, `Agent`, `Goal`, `TaskItem` entities + repositories.
- `POST /organizations`, `POST /organizations/{id}/roles`, `POST
  /organizations/{id}/teams` implemented and tested.
- A minimal `Decomposer` that (for now) creates exactly one `TaskItem` per `Goal`
  (multi-task decomposition arrives in Phase 2).
- A minimal `Matcher` that assigns the task to any idle agent of a matching role, or
  creates one via `AgentFactory` (Factory Method pattern).
- A **non-batched** direct call to `AnthropicProvider` completes one task end-to-end.
- `ExecutionLog` row is written on task completion (FR-22 — logging starts now, not
  later).

**DoD (automated, every commit)**: with a mocked `LlmProvider` returning a fixed
fixture response, `POST /organizations/{id}/goals` returns a completed task with a
non-null `actual_cost_usd` (computed from the fixture's reported token counts) and an
`execution_log` row, run as part of `mvn test` with no external network calls or API
cost — mirroring the mock-first philosophy used from Phase 3 onward (§7), applied
consistently starting here rather than only later.

**DoD (manual, once per phase, real key)**: the same flow, run once against the real
Anthropic API with `ANTHROPIC_API_KEY` set, confirming the mocked contract actually
matches live behavior. This manual check is what actually validates the integration;
the automated check only validates the code path around it.

### Phase 2 — Communication + Tracing (Week 4–6)

- `Message` entity + `run_id`/`trace_id` propagation through the Decomposer→Matcher→
  Dispatcher path.
- `ConstraintEngine` topology enforcement (FR-21) for `HIERARCHICAL` teams.
- Multi-task `Decomposer` (a single Goal can now produce several `TaskItem`s with
  skill tags).

**DoD**: A goal that decomposes into 3+ tasks across 2 roles produces a fully
reconstructable message trace via `SELECT * FROM message WHERE run_id = ?`.

### Phase 3 — Scored Batch Scheduler (Week 7–9) — Core Innovation

- `BatchCompatibilityChecker` (dependency, budget, and org-boundary filtering — this
  runs *before* scoring, not after; see `design.md` §4 for why grouping by model tier
  alone is not sufficient to satisfy FR-11).
- `SchedulingStrategy` / `WeightedSchedulingStrategy` with normalized [0,1] score
  components (Strategy pattern; see `design.md` §4 for the normalization fix).
- `BatchScheduler` + `PromptMultiplexer` + `Dispatcher`, per `design.md` §4.
- Isolated-retry-on-malformed-slice logic (FR-10).
- **Benchmark harness**: a test/CLI runner that executes an identical task set through
  (a) the batched path and (b) a naive one-call-per-agent path, recording **input
  tokens, output tokens, total tokens, API call count, cost, and latency** for both —
  cost/call-count alone is not sufficient evidence that batching helped (see
  `design.md` §9's note on why token counts must be reported explicitly).

**DoD**: Benchmark harness produces a comparison report (input/output/total tokens,
API call count, cost, latency, batch fill ratio) for at least 20 tasks across 5
agents, with the batched path showing fewer API calls than the naive path on the same
task set. A second, dedicated test confirms two tasks with a declared
`parentTaskId` dependency are **never** placed in the same batch, directly
verifying FR-11 rather than assuming the compatibility checker works.

### Phase 4 — Memory + Explainability (Week 10–11)

- `EmbeddingProvider` / `VoyageEmbeddingProvider`.
- `MemoryService.store` / `recall` (vector search).
- `MemoryRelation` + `explainWhy` (recursive CTE, `design.md` §6 — note the explicit
  `from`/`to` direction convention stated there; relations written inconsistently with
  it will silently break the traversal).

**DoD**: A decision made during Phase 3's benchmark run can be queried via
`GET /memory/decisions/{id}/explain` and returns at least one `EVIDENCE` relation.

### Phase 5 — Governance + Resilience (Week 12–13)

- `BudgetTracker` (writing to `organization.spent_budget_usd` / `agent.tokens_used` in
  the same transaction as the `execution_log` insert — see `design.md` §7.1),
  `ApprovalGate` (Command pattern), `EscalationHandler` chain (Chain of
  Responsibility).
- `ResilientLlmGateway` (Decorator + Resilience4j retry/circuit breaker), fallback
  tier logic, `DeadLetterEntry` persistence.

**DoD**: An integration test simulates an LLM timeout and asserts the sequence
retry → fallback-to-HAIKU → (if HAIKU also fails) dead-letter row created, without an
unhandled exception reaching the controller layer. A second test asserts an action
with `effectType = SPEND` is held in `PENDING` status until `POST
/approvals/{id}/resolve` is called. A third test asserts that after a completed task,
`organization.spent_budget_usd` exactly equals `SUM(actual_cost_usd)` from
`execution_log` for that organization — directly verifying the single-source-of-truth
rule rather than assuming the two never drift.

### Phase 6 — Intelligence & Analytics (Week 14–15)

- `SyntheticGoalGenerator` (FR-23).
- `HistoricalAverageModel`, `LinearRegressionCostModel`, `TreeRegressionCostModel`,
  trained only on the **pre-execution** feature view (Tribuo-based, `design.md` §11 —
  `batch_size_at_execution` and `context_overlap_score` are analysis-only features and
  must not leak into the live model's training data).
- Nightly training job; `POST /predict/cost` endpoint wired to the best-performing
  trained model with cold-start fallback.

**DoD**: With ≥200 synthetic + real execution_log rows present, all three models
report MAE/RMSE/R² on a held-out chronological split, and `/predict/cost` returns a
non-fallback prediction. A test confirms the request/response schema for
`/predict/cost` contains none of the post-execution-only fields (`batchSizeAtExecution`,
`contextOverlapScore`) — i.e. the live model was never handed a feature it can't have.

### Phase 7 — Demo, Evaluation, Report (Week 16)

- `/demo/seed` endpoint creates the AI Product Team org (Researcher, Writer, Reviewer)
  used throughout `requirements.md` §8's acceptance criteria.
- Full acceptance-criteria checklist (`requirements.md` §8) run and results recorded.
- Statistical evaluation: ≥50 repeated synthetic runs, mean ± std dev reported for the
  full comparison table below, batched vs. naive baseline.

**Final evaluation table** (this is the centerpiece result for the report/viva — every
column must be a measured number, not an assumed or derived-only figure):

| Metric | Naive baseline | SYNAPSE |
|---|---|---|
| Tasks | 50 | 50 |
| Agents | 5 | 5 |
| API calls | 50 | (measured) |
| Input tokens | (measured) | (measured) |
| Output tokens | (measured) | (measured) |
| Total tokens | (measured) | (measured) |
| Total cost (USD) | (measured) | (measured) |
| Mean latency (ms) | (measured) | (measured) |
| P95 latency (ms) | (measured) | (measured) |
| Batch fill ratio | N/A | (measured) |
| Failed tasks | (measured) | (measured) |
| Recovered (via retry/fallback) | (measured) | (measured) |

**DoD**: All 7 acceptance criteria in `requirements.md` §8 pass; the table above is
fully populated with real measured values (mean ± std dev across ≥50 runs) for the
report, with input/output tokens reported explicitly rather than cost alone.

---

## 7. Testing Strategy

| Layer | Approach |
|---|---|
| Unit | JUnit 5 + Mockito. `WeightedSchedulingStrategy` scoring, `EscalationHandler` chain resolution, `ApprovalGate` trigger logic, `ConstraintEngine` routing rules — all pure-logic, fully mockable via the interfaces in `design.md` §4. |
| Integration | Testcontainers (`pgvector/pgvector:pg16` image) + `@SpringBootTest`. Full goal → decomposition → batch → dispatch flow with a **mocked** `LlmProvider` (no real API cost during CI). |
| Contract | A dedicated test class asserts `AgentBatchResponse` parses correctly for batch sizes 1, 3, 5, 10 against fixture JSON strings, catching structured-output drift independent of the live API. |
| Resilience | Simulated timeout/malformed-response tests per Phase 5 DoD above. |
| Benchmark | Not a unit test — a standalone runner (`BenchmarkRunner`, invoked via `mvn exec:java` or a `/demo/benchmark` dev-only endpoint) that hits the **real** Anthropic API for the primary evaluation metrics; run manually, not on every CI build, to control cost. |
| Model evaluation | A test asserting each trained model's MAE on a fixed fixture dataset stays within an expected range, catching silent regressions in the training pipeline. |

CI (if configured) runs Unit + Integration + Contract on every push; Benchmark and
live-API tests are run manually before each phase's DoD check-off to avoid burning
API budget on every commit.

---

## 8. Demo Script (Phase 7)

1. `docker compose up -d && mvn spring-boot:run`
2. `POST /demo/seed` → creates "AI Product Team" org with Researcher (HAIKU),
   Writer (SONNET), Reviewer (SONNET) roles and a hierarchical team.
3. `POST /organizations/{id}/goals` with `{"description": "Research and draft a
   one-page product brief for a subscription meal-kit app targeting college students"}`.
4. Poll `GET /tasks/{id}` for each resulting task until `DONE`.
5. `GET /metrics/runs/{goalId}` — show call count, cost, latency, batch fill ratio.
6. `GET /memory/decisions/{id}/explain` on the Writer's key decision — show evidence +
   rejected alternative.
7. Submit a goal that includes a spend-tagged action (e.g., "and schedule a $50 ad
   test") — show it land in `PENDING` via `GET /approvals?status=PENDING`, then
   resolve it via `POST /approvals/{id}/resolve`.
8. `POST /predict/cost` for a new, not-yet-run task — show a real (non-fallback)
   prediction once ≥200 execution_log rows exist.

---

## 9. Risk Register

| Risk | Likelihood | Mitigation |
|---|---|---|
| Anthropic or Voyage API cost exceeds student budget during development | Medium | Default all dev-profile roles to `HAIKU` tier; benchmark/live tests run manually, not on every commit (§7) |
| Structured JSON output drifts/breaks under real model responses | Medium | Contract tests (§7) catch this early; isolated per-agent retry (FR-10) contains the blast radius |
| Not enough real execution data by Phase 6 to train meaningful models | Medium | `SyntheticGoalGenerator` (Phase 1, used continuously) — do not wait until Phase 6 to start generating data |
| pgvector `ivfflat` index performs poorly on a very small dataset | Low | Acceptable for project scale; document as a known limitation, not a blocker |
| Solo/small-team bandwidth vs. 16-week plan | Medium | Phases 4 and 6 are the most cuttable in scope (fewer relation types, 2 models instead of 3) if behind schedule — cut breadth, not the batching benchmark (Phase 3), which is the core deliverable |

---

## 10. Project-Level Definition of Done

The implementation is complete when:

1. All 7 phases' individual DoD criteria (§6) are met.
2. All 7 acceptance criteria in `requirements.md` §8 pass in a single, reproducible
   run from a clean `docker compose up`.
3. `mvn test` passes with no disabled/skipped tests other than the intentionally
   manual Benchmark suite (§7).
4. No secret, API key, or credential appears in source control (verify with
   `git log -p | grep -i "sk-ant\|pa-"` before final submission).
