package com.synapse.scheduler;

import com.synapse.core.entity.TaskItem;

import java.util.ArrayList;
import java.util.List;

public class BatchCompatibilityChecker {
    public boolean canBatchTogether(TaskItem left, TaskItem right) {
        if (left == null || right == null || left.equals(right)) {
            return false;
        }
        if (left.getOrganization() == null || right.getOrganization() == null) {
            return false;
        }
        if (!left.getOrganization().getId().equals(right.getOrganization().getId())) {
            return false;
        }
        if (left.getStatus() == null || right.getStatus() == null) {
            return false;
        }
        if (left.getParentTask() != null && left.getParentTask().equals(right)) {
            return false;
        }
        if (right.getParentTask() != null && right.getParentTask().equals(left)) {
            return false;
        }
        if (hasDependencyCycle(left, right)) {
            return false;
        }
        return true;
    }

    public List<List<TaskItem>> partitionIntoCompatibleBatches(List<TaskItem> tasks, int maxBatchSize) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }

        List<TaskItem> remaining = new ArrayList<>(tasks);
        List<List<TaskItem>> batches = new ArrayList<>();

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

            batches.add(batch);
        }

        return batches;
    }

    private boolean hasDependencyCycle(TaskItem left, TaskItem right) {
        if (left.getParentTask() != null && left.getParentTask().equals(right)) {
            return true;
        }
        if (right.getParentTask() != null && right.getParentTask().equals(left)) {
            return true;
        }
        return false;
    }

    private boolean isBatchCompatible(List<TaskItem> batch, TaskItem candidate) {
        for (TaskItem current : batch) {
            if (!canBatchTogether(current, candidate)) {
                return false;
            }
        }
        return true;
    }
}
