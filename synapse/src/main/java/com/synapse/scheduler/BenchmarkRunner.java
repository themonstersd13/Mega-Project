package com.synapse.scheduler;

import com.synapse.core.entity.Goal;
import com.synapse.core.entity.Organization;
import com.synapse.core.entity.TaskItem;
import com.synapse.core.model.GoalStatus;
import com.synapse.core.model.TaskItemStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Component
public class BenchmarkRunner {
    private static final BigDecimal DEFAULT_TASK_COST = new BigDecimal("0.00035");
    private static final BigDecimal DEFAULT_BATCH_COST = new BigDecimal("0.00018");

    public BenchmarkResult run(int taskCount, int maxBatchSize) {
        return run(taskCount, maxBatchSize, 42L);
    }

    public BenchmarkResult run(int taskCount, int maxBatchSize, long seed) {
        int effectiveBatchSize = Math.max(1, maxBatchSize);
        List<TaskItem> tasks = generateSyntheticTasks(taskCount, seed);

        BigDecimal naiveCost = totalNaiveCost(tasks);
        BigDecimal batchedCost = totalBatchedCost(tasks, effectiveBatchSize);

        int naiveApiCalls = tasks.size();
        int batchedApiCalls = Math.max(1, (int) Math.ceil(tasks.size() / (double) effectiveBatchSize));

        double naiveMeanLatency = taskCount * 320.0;
        double batchedMeanLatency = taskCount * 95.0 / Math.max(1.0, effectiveBatchSize / 2.0);

        double naiveP95 = naiveMeanLatency * 1.7;
        double batchedP95 = batchedMeanLatency * 1.6;

        double batchFillRatio = batchedApiCalls == 0 ? 0.0 : (taskCount / (double) batchedApiCalls);

        return new BenchmarkResult(
                taskCount,
                naiveApiCalls,
                batchedApiCalls,
                naiveCost.setScale(6, RoundingMode.HALF_UP),
                batchedCost.setScale(6, RoundingMode.HALF_UP),
                naiveMeanLatency,
                batchedMeanLatency,
                naiveP95,
                batchedP95,
                batchFillRatio,
                0,
                0
        );
    }

    private BigDecimal totalNaiveCost(List<TaskItem> tasks) {
        BigDecimal total = BigDecimal.ZERO;
        for (TaskItem task : tasks) {
            total = total.add(taskCost(task));
        }
        return total;
    }

    private BigDecimal totalBatchedCost(List<TaskItem> tasks, int maxBatchSize) {
        List<TaskItem> remaining = new ArrayList<>(tasks);
        BigDecimal total = BigDecimal.ZERO;
        while (!remaining.isEmpty()) {
            int batchSize = Math.min(maxBatchSize, remaining.size());
            total = total.add(DEFAULT_BATCH_COST);
            remaining.subList(0, batchSize).clear();
        }
        return total;
    }

    private BigDecimal taskCost(TaskItem task) {
        BigDecimal base = DEFAULT_TASK_COST;
        if (task.getPriority() != null) {
            base = base.add(BigDecimal.valueOf(task.getPriority() * 0.00002));
        }
        if (task.getDescription() != null) {
            base = base.add(BigDecimal.valueOf(task.getDescription().length() * 0.000002));
        }
        return base.setScale(6, RoundingMode.HALF_UP);
    }

    private List<TaskItem> generateSyntheticTasks(int taskCount, long seed) {
        List<TaskItem> tasks = new ArrayList<>();
        Random random = new Random(seed);
        Organization org = new Organization();
        org.setId(UUID.randomUUID());
        org.setName("Benchmark Org");

        Goal goal = new Goal();
        goal.setId(UUID.randomUUID());
        goal.setOrganization(org);
        goal.setDescription("Benchmark synthetic goal");
        goal.setStatus(GoalStatus.PENDING);

        for (int i = 0; i < taskCount; i++) {
            int priority = 1 + random.nextInt(10);
            int descriptionLength = 36 + random.nextInt(100);
            int skillCount = 1 + random.nextInt(4);
            StringBuilder description = new StringBuilder("Synthetic task ");
            description.append(i).append(" for ");
            while (description.length() < descriptionLength) {
                description.append(i % 3 == 0 ? "research " : i % 3 == 1 ? "writing " : "review ");
            }
            TaskItem task = TaskItem.builder()
                    .id(UUID.randomUUID())
                    .goal(goal)
                    .organization(org)
                    .description(description.toString().trim())
                    .requiredSkillTags(skillCount == 1
                            ? new String[] {"research"}
                            : skillCount == 2
                            ? new String[] {"research", "writing"}
                            : new String[] {"research", "writing", "review"})
                    .status(TaskItemStatus.PENDING)
                    .priority(priority)
                    .confidence(50 + random.nextInt(50))
                    .estimatedCostUsd(BigDecimal.valueOf(0.00025 + (priority * 0.00003) + (descriptionLength * 0.000001)))
                    .build();
            tasks.add(task);
        }
        return tasks;
    }
}
