package com.synapse.scheduler;

import com.synapse.core.entity.TaskItem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class BatchScheduler {
    private final SchedulingStrategy schedulingStrategy;
    private final BatchCompatibilityChecker compatibilityChecker;

    public BatchScheduler() {
        this(new WeightedSchedulingStrategy());
    }

    public BatchScheduler(SchedulingStrategy schedulingStrategy) {
        this(schedulingStrategy, new BatchCompatibilityChecker());
    }

    public BatchScheduler(SchedulingStrategy schedulingStrategy, BatchCompatibilityChecker compatibilityChecker) {
        this.schedulingStrategy = schedulingStrategy;
        this.compatibilityChecker = compatibilityChecker;
    }

    public List<TaskBatch> schedule(List<TaskItem> tasks, int maxBatchSize) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }

        List<TaskItem> sorted = new ArrayList<>(tasks);
        sorted.sort(Comparator.comparingDouble(this::score).reversed());

        List<TaskItem> remaining = new ArrayList<>(sorted);
        List<TaskBatch> batches = new ArrayList<>();

        while (!remaining.isEmpty()) {
            TaskItem seed = remaining.remove(0);
            List<TaskItem> batch = new ArrayList<>();
            batch.add(seed);

            for (int i = remaining.size() - 1; i >= 0; i--) {
                TaskItem candidate = remaining.get(i);
                if (batch.size() >= maxBatchSize) {
                    break;
                }
                if (isBatchCompatible(batch, candidate)) {
                    batch.add(candidate);
                    remaining.remove(i);
                }
            }

            batches.add(TaskBatch.of(batch));
        }

        return batches;
    }

    private boolean isBatchCompatible(List<TaskItem> batch, TaskItem candidate) {
        for (TaskItem current : batch) {
            if (!compatibilityChecker.canBatchTogether(current, candidate)) {
                return false;
            }
        }
        return true;
    }

    private double score(TaskItem task) {
        return schedulingStrategy.score(task);
    }
}
