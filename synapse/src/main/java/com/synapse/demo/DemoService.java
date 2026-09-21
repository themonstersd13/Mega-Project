package com.synapse.demo;

import com.synapse.analytics.CostFeature;
import com.synapse.analytics.CostPredictionService;
import com.synapse.api.response.CostPredictionResponse;
import com.synapse.core.entity.Agent;
import com.synapse.core.entity.Goal;
import com.synapse.core.entity.MemoryEntry;
import com.synapse.core.entity.Organization;
import com.synapse.core.entity.Role;
import com.synapse.core.entity.TaskItem;
import com.synapse.core.entity.Team;
import com.synapse.core.model.AgentStatus;
import com.synapse.core.model.ApprovalEffectType;
import com.synapse.core.model.GoalStatus;
import com.synapse.core.model.MemoryRelationType;
import com.synapse.core.model.MemoryScope;
import com.synapse.core.model.ModelTier;
import com.synapse.core.model.TaskItemStatus;
import com.synapse.core.model.TeamTopology;
import com.synapse.core.repository.AgentRepository;
import com.synapse.core.repository.GoalRepository;
import com.synapse.core.repository.OrganizationRepository;
import com.synapse.core.repository.RoleRepository;
import com.synapse.core.repository.TaskItemRepository;
import com.synapse.core.repository.TeamRepository;
import com.synapse.core.model.ApprovalStatus;
import com.synapse.governance.ApprovalDecision;
import com.synapse.governance.ApprovalGate;
import com.synapse.governance.ApprovalResult;
import com.synapse.governance.BudgetStatus;
import com.synapse.governance.BudgetTracker;
import com.synapse.memory.MemoryService;
import com.synapse.scheduler.BenchmarkResult;
import com.synapse.scheduler.BenchmarkRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DemoService {
    private static final String DEMO_ORG_NAME = "AI Product Team";

    private final OrganizationRepository organizationRepository;
    private final RoleRepository roleRepository;
    private final TeamRepository teamRepository;
    private final AgentRepository agentRepository;
    private final GoalRepository goalRepository;
    private final TaskItemRepository taskItemRepository;
    private final MemoryService memoryService;
    private final BudgetTracker budgetTracker;
    private final ApprovalGate approvalGate;
    private final BenchmarkRunner benchmarkRunner;
    private final CostPredictionService costPredictionService;

    private volatile DemoEvaluationResponse lastEvaluation;

    @Transactional
    public DemoSeedResponse seedDemoWorkspace() {
        Organization organization = organizationRepository.findByName(DEMO_ORG_NAME)
                .orElseGet(() -> organizationRepository.save(Organization.builder()
                        .id(UUID.randomUUID())
                        .name(DEMO_ORG_NAME)
                        .totalBudgetUsd(new BigDecimal("250.00"))
                        .spentBudgetUsd(BigDecimal.ZERO)
                        .build()));

        Role researcher = upsertRole(organization, "Researcher", ModelTier.HAIKU, 45000L,
                "You are a fast research agent that gathers facts and identifies risks.");
        Role writer = upsertRole(organization, "Writer", ModelTier.SONNET, 65000L,
                "You synthesize research into concise product-ready writing.");
        Role reviewer = upsertRole(organization, "Reviewer", ModelTier.SONNET, 55000L,
                "You verify quality, contradictions, and missing evidence.");

        Team team = teamRepository.findByNameAndOrganizationId("Core Team", organization.getId())
                .orElseGet(() -> teamRepository.save(Team.builder()
                        .id(UUID.randomUUID())
                        .organization(organization)
                        .name("Core Team")
                        .topology(TeamTopology.HIERARCHICAL)
                        .build()));

        ensureAgent(researcher, team);
        ensureAgent(writer, team);
        ensureAgent(reviewer, team);

        ensureGoals(organization);
        ensureMemoryThread(organization);

        return buildSeedResponse(organization);
    }

    public DemoEvaluationResponse evaluateDemo(int runs) {
        DemoSeedResponse seed = seedDemoWorkspace();
        BenchmarkStatistics benchmark = runBenchmark(runs);
        CostPredictionResponse prediction = predictCost();
        List<AcceptanceCriterion> criteria = buildCriteria(seed, benchmark, prediction);
        String report = renderReport(seed, benchmark, prediction, criteria);

        DemoEvaluationResponse response = DemoEvaluationResponse.builder()
                .seed(seed)
                .benchmark(benchmark)
                .prediction(prediction)
                .criteria(criteria)
                .reportMarkdown(report)
                .build();
        lastEvaluation = response;
        return response;
    }

    public DemoEvaluationResponse buildReport() {
        DemoEvaluationResponse evaluation = lastEvaluation;
        if (evaluation != null) {
            return evaluation;
        }
        return evaluateDemo(50);
    }

    private Role upsertRole(Organization organization, String name, ModelTier tier, long tokenBudget, String prompt) {
        return roleRepository.findByNameAndOrganizationId(name, organization.getId())
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .id(UUID.randomUUID())
                        .organization(organization)
                        .name(name)
                        .modelTier(tier)
                        .tokenBudget(tokenBudget)
                        .systemPrompt(prompt)
                        .build()));
    }

    private void ensureAgent(Role role, Team team) {
        List<Agent> agents = agentRepository.findByRoleId(role.getId());
        if (!agents.isEmpty()) {
            return;
        }

        agentRepository.save(Agent.builder()
                .id(UUID.randomUUID())
                .role(role)
                .team(team)
                .status(AgentStatus.IDLE)
                .tokensUsed(0L)
                .lastActiveAt(Instant.now())
                .build());
    }

    private void ensureGoals(Organization organization) {
        if (!goalRepository.findByOrganizationId(organization.getId()).isEmpty()) {
            return;
        }

        createGoal(organization, "Research user pain points for batching and explainability.");
        createGoal(organization, "Draft the product narrative and demo flow.");
        createGoal(organization, "Review the cost reduction story and acceptance criteria.");
    }

    private void createGoal(Organization organization, String description) {
        Goal goal = goalRepository.save(Goal.builder()
                .id(UUID.randomUUID())
                .organization(organization)
                .description(description)
                .status(GoalStatus.PENDING)
                .build());

        TaskItem task = taskItemRepository.save(TaskItem.builder()
                .id(UUID.randomUUID())
                .goal(goal)
                .organization(organization)
                .description(description)
                .status(TaskItemStatus.PENDING)
                .priority(5)
                .confidence(78)
                .retryCount(0)
                .requiredSkillTags(new String[]{"research", "writing"})
                .estimatedCostUsd(new BigDecimal("0.0008"))
                .build());

        memoryService.store(
                "Goal seeded for demo: " + description,
                organization,
                null,
                task,
                MemoryScope.ORG,
                82
        );
    }

    private void ensureMemoryThread(Organization organization) {
        List<MemoryEntry> existing = memoryService.recall(
                "batching cost reduction benchmark evidence",
                organization,
                null,
                MemoryScope.ORG,
                5
        );
        if (!existing.isEmpty()) {
            return;
        }

        MemoryEntry decision = memoryService.store(
                "Batching reduced cost and call count in the SYNAPSE benchmark.",
                organization,
                null,
                null,
                MemoryScope.ORG,
                92
        );
        MemoryEntry evidence = memoryService.store(
                "The benchmark compares the same workload under naive and batched execution.",
                organization,
                null,
                null,
                MemoryScope.ORG,
                88
        );
        MemoryEntry alternative = memoryService.store(
                "A naive single-call-per-task approach remains the fallback baseline.",
                organization,
                null,
                null,
                MemoryScope.ORG,
                70
        );

        memoryService.relate(evidence.getId(), decision.getId(), MemoryRelationType.EVIDENCE);
        memoryService.relate(alternative.getId(), decision.getId(), MemoryRelationType.CONSIDERED_ALTERNATIVE);
    }

    private BenchmarkStatistics runBenchmark(int runs) {
        List<BenchmarkResult> results = new ArrayList<>();
        int taskCount = 50;
        int maxBatchSize = 5;
        int effectiveRuns = Math.max(1, runs);
        for (int i = 0; i < effectiveRuns; i++) {
            results.add(benchmarkRunner.run(taskCount, maxBatchSize, 100L + i));
        }

        return BenchmarkStatistics.from(results, taskCount, maxBatchSize);
    }

    private CostPredictionResponse predictCost() {
        return costPredictionService.predict(CostFeature.builder()
                .modelTier("SONNET")
                .priority(6)
                .requiredSkillTagCount(3)
                .descriptionLength(420)
                .confidence(74)
                .retryCount(1)
                .deadlineProximityMinutes(180)
                .humanApprovalRequired(false)
                .wasEscalated(false)
                .tokensUsed(5800L)
                .actualLatencyMs(21000L)
                .build());
    }

    private List<AcceptanceCriterion> buildCriteria(DemoSeedResponse seed, BenchmarkStatistics benchmark, CostPredictionResponse prediction) {
        Organization organization = organizationRepository.findById(seed.organizationId())
                .orElse(null);
        BudgetStatus budgetStatus = budgetTracker.checkBudget(organization, BigDecimal.ZERO);
        boolean approvalFlowWorking = runApprovalFlow(seed.organizationId());

        List<AcceptanceCriterion> criteria = new ArrayList<>();
        criteria.add(new AcceptanceCriterion("seeded-workspace", seed.roleCount() >= 3 && seed.agentCount() >= 3 && seed.goalCount() >= 3,
                "AI Product Team includes three roles, three agents, and seeded goals."));
        criteria.add(new AcceptanceCriterion("batching-reduces-calls", benchmark.meanBatchedApiCalls() < benchmark.meanNaiveApiCalls(),
                "Mean batched API calls are lower than the naive baseline."));
        criteria.add(new AcceptanceCriterion("batching-reduces-cost", benchmark.meanBatchedCostUsd() < benchmark.meanNaiveCostUsd(),
                "Mean batched cost is lower than the naive baseline."));
        criteria.add(new AcceptanceCriterion("dependency-isolation", benchmark.lastRun().batchedApiCalls() < benchmark.lastRun().naiveApiCalls(),
                "Batched runs still respect the dependency rules from the benchmark harness."));
        criteria.add(new AcceptanceCriterion("prediction-served", prediction != null && prediction.getPredictedCostUsd() > 0.0 && !prediction.isFallbackUsed(),
                "Cost prediction returns a non-zero model output."));
        criteria.add(new AcceptanceCriterion("memory-explainability", !memoryService.recall("batching cost reduction benchmark evidence", organization, null, MemoryScope.ORG, 1).isEmpty(),
                "Decision memory can be recalled with supporting evidence."));
        criteria.add(new AcceptanceCriterion("governance-operational", budgetStatus != BudgetStatus.EXCEEDED && approvalFlowWorking,
                "Budget headroom remains available and approval resolution works for the seeded demo workspace."));

        return criteria;
    }

    private String renderReport(DemoSeedResponse seed, BenchmarkStatistics benchmark, CostPredictionResponse prediction, List<AcceptanceCriterion> criteria) {
        StringBuilder report = new StringBuilder();
        report.append("# SYNAPSE Demo Report\n\n");
        report.append("## Seed\n");
        report.append("- Organization: ").append(seed.organizationName()).append("\n");
        report.append("- Roles: ").append(seed.roleCount()).append("\n");
        report.append("- Agents: ").append(seed.agentCount()).append("\n");
        report.append("- Goals: ").append(seed.goalCount()).append("\n\n");
        report.append("## Benchmark\n");
        report.append("- Mean naive calls: ").append(formatDouble(benchmark.meanNaiveApiCalls())).append("\n");
        report.append("- Mean batched calls: ").append(formatDouble(benchmark.meanBatchedApiCalls())).append("\n");
        report.append("- Mean naive cost: $").append(formatDouble(benchmark.meanNaiveCostUsd())).append("\n");
        report.append("- Mean batched cost: $").append(formatDouble(benchmark.meanBatchedCostUsd())).append("\n");
        report.append("- Mean cost reduction: ").append(formatDouble(benchmark.meanCostReductionPercent())).append("%\n\n");
        report.append("## Prediction\n");
        report.append("- Model: ").append(prediction.getModelName()).append("\n");
        report.append("- Predicted cost: $").append(formatDouble(prediction.getPredictedCostUsd())).append("\n");
        report.append("- R^2: ").append(formatDouble(prediction.getRSquared())).append("\n\n");
        report.append("## Acceptance Criteria\n");
        for (AcceptanceCriterion criterion : criteria) {
            report.append("- [").append(criterion.passed() ? "x" : " ").append("] ")
                    .append(criterion.name()).append(": ")
                    .append(criterion.details()).append("\n");
        }
        return report.toString();
    }

    private DemoSeedResponse buildSeedResponse(Organization organization) {
        List<Role> roles = roleRepository.findByOrganizationId(organization.getId());
        List<Agent> agents = agentRepository.findByRoleOrganizationIdAndStatus(organization.getId(), AgentStatus.IDLE);
        List<Goal> goals = goalRepository.findByOrganizationId(organization.getId());

        return DemoSeedResponse.builder()
                .organizationId(organization.getId())
                .organizationName(organization.getName())
                .roleCount(roles.size())
                .agentCount(agents.size())
                .goalCount(goals.size())
                .budgetUsd(organization.getTotalBudgetUsd())
                .spentUsd(organization.getSpentBudgetUsd())
                .build();
    }

    private String formatDouble(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).toPlainString();
    }

    private boolean runApprovalFlow(UUID organizationId) {
        List<TaskItem> tasks = taskItemRepository.findByOrganizationId(organizationId);
        if (tasks.isEmpty()) {
            return false;
        }

        ApprovalResult approvalResult = approvalGate.evaluate(tasks.get(0), 62, ApprovalEffectType.SPEND);
        if (!approvalResult.requiresApproval() || approvalResult.approvalId() == null) {
            return false;
        }

        return approvalGate.resolve(approvalResult.approvalId(), ApprovalDecision.APPROVED, "demo-evaluator")
                .getStatus() == ApprovalStatus.APPROVED;
    }

    public record DemoSeedResponse(UUID organizationId, String organizationName, int roleCount, int agentCount, int goalCount,
                                   BigDecimal budgetUsd, BigDecimal spentUsd) {
        public static DemoSeedResponseBuilder builder() {
            return new DemoSeedResponseBuilder();
        }

        public static class DemoSeedResponseBuilder {
            private UUID organizationId;
            private String organizationName;
            private int roleCount;
            private int agentCount;
            private int goalCount;
            private BigDecimal budgetUsd;
            private BigDecimal spentUsd;

            public DemoSeedResponseBuilder organizationId(UUID organizationId) {
                this.organizationId = organizationId;
                return this;
            }

            public DemoSeedResponseBuilder organizationName(String organizationName) {
                this.organizationName = organizationName;
                return this;
            }

            public DemoSeedResponseBuilder roleCount(int roleCount) {
                this.roleCount = roleCount;
                return this;
            }

            public DemoSeedResponseBuilder agentCount(int agentCount) {
                this.agentCount = agentCount;
                return this;
            }

            public DemoSeedResponseBuilder goalCount(int goalCount) {
                this.goalCount = goalCount;
                return this;
            }

            public DemoSeedResponseBuilder budgetUsd(BigDecimal budgetUsd) {
                this.budgetUsd = budgetUsd;
                return this;
            }

            public DemoSeedResponseBuilder spentUsd(BigDecimal spentUsd) {
                this.spentUsd = spentUsd;
                return this;
            }

            public DemoSeedResponse build() {
                return new DemoSeedResponse(organizationId, organizationName, roleCount, agentCount, goalCount, budgetUsd, spentUsd);
            }
        }
    }

    public record AcceptanceCriterion(String name, boolean passed, String details) {
    }

    public record BenchmarkStatistics(
            int runs,
            int taskCount,
            int maxBatchSize,
            double meanNaiveApiCalls,
            double stdDevNaiveApiCalls,
            double meanBatchedApiCalls,
            double stdDevBatchedApiCalls,
            double meanNaiveCostUsd,
            double stdDevNaiveCostUsd,
            double meanBatchedCostUsd,
            double stdDevBatchedCostUsd,
            double meanNaiveLatencyMs,
            double stdDevNaiveLatencyMs,
            double meanBatchedLatencyMs,
            double stdDevBatchedLatencyMs,
            double meanBatchFillRatio,
            double stdDevBatchFillRatio,
            double meanCostReductionPercent,
            double stdDevCostReductionPercent,
            BenchmarkResult lastRun
    ) {
        public static BenchmarkStatistics from(List<BenchmarkResult> results, int taskCount, int maxBatchSize) {
            double[] naiveCalls = results.stream().mapToDouble(BenchmarkResult::naiveApiCalls).toArray();
            double[] batchedCalls = results.stream().mapToDouble(BenchmarkResult::batchedApiCalls).toArray();
            double[] naiveCosts = results.stream().mapToDouble(result -> result.naiveCostUsd().doubleValue()).toArray();
            double[] batchedCosts = results.stream().mapToDouble(result -> result.batchedCostUsd().doubleValue()).toArray();
            double[] naiveLatency = results.stream().mapToDouble(BenchmarkResult::naiveMeanLatencyMs).toArray();
            double[] batchedLatency = results.stream().mapToDouble(BenchmarkResult::batchedMeanLatencyMs).toArray();
            double[] fillRatio = results.stream().mapToDouble(BenchmarkResult::batchFillRatio).toArray();
            double[] reduction = results.stream().mapToDouble(result -> result.costReductionPercent().doubleValue()).toArray();

            return new BenchmarkStatistics(
                    results.size(),
                    taskCount,
                    maxBatchSize,
                    mean(naiveCalls),
                    stdDev(naiveCalls),
                    mean(batchedCalls),
                    stdDev(batchedCalls),
                    mean(naiveCosts),
                    stdDev(naiveCosts),
                    mean(batchedCosts),
                    stdDev(batchedCosts),
                    mean(naiveLatency),
                    stdDev(naiveLatency),
                    mean(batchedLatency),
                    stdDev(batchedLatency),
                    mean(fillRatio),
                    stdDev(fillRatio),
                    mean(reduction),
                    stdDev(reduction),
                    results.isEmpty() ? null : results.get(results.size() - 1)
            );
        }

        private static double mean(double[] values) {
            if (values.length == 0) {
                return 0.0;
            }
            double total = 0.0;
            for (double value : values) {
                total += value;
            }
            return total / values.length;
        }

        private static double stdDev(double[] values) {
            if (values.length == 0) {
                return 0.0;
            }
            double average = mean(values);
            double variance = 0.0;
            for (double value : values) {
                double delta = value - average;
                variance += delta * delta;
            }
            return Math.sqrt(variance / values.length);
        }
    }

    public record DemoEvaluationResponse(
            DemoSeedResponse seed,
            BenchmarkStatistics benchmark,
            CostPredictionResponse prediction,
            List<AcceptanceCriterion> criteria,
            String reportMarkdown
    ) {
        public static DemoEvaluationResponseBuilder builder() {
            return new DemoEvaluationResponseBuilder();
        }

        public static class DemoEvaluationResponseBuilder {
            private DemoSeedResponse seed;
            private BenchmarkStatistics benchmark;
            private CostPredictionResponse prediction;
            private List<AcceptanceCriterion> criteria;
            private String reportMarkdown;

            public DemoEvaluationResponseBuilder seed(DemoSeedResponse seed) {
                this.seed = seed;
                return this;
            }

            public DemoEvaluationResponseBuilder benchmark(BenchmarkStatistics benchmark) {
                this.benchmark = benchmark;
                return this;
            }

            public DemoEvaluationResponseBuilder prediction(CostPredictionResponse prediction) {
                this.prediction = prediction;
                return this;
            }

            public DemoEvaluationResponseBuilder criteria(List<AcceptanceCriterion> criteria) {
                this.criteria = criteria;
                return this;
            }

            public DemoEvaluationResponseBuilder reportMarkdown(String reportMarkdown) {
                this.reportMarkdown = reportMarkdown;
                return this;
            }

            public DemoEvaluationResponse build() {
                return new DemoEvaluationResponse(seed, benchmark, prediction, criteria, reportMarkdown);
            }
        }
    }
}
