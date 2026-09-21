package com.synapse.scheduler;

import com.synapse.core.entity.TaskItem;

import java.time.Duration;
import java.time.LocalDateTime;

public class WeightedSchedulingStrategy implements SchedulingStrategy {
    @Override
    public double score(TaskItem task) {
        if (task == null) {
            return 0.0;
        }

        double priorityScore = normalizePriority(task.getPriority());
        double deadlineScore = normalizeDeadline(task.getDeadline());
        double costScore = normalizeEstimatedCost(task.getEstimatedCostUsd());
        double confidenceScore = normalizeConfidence(task.getConfidence());
        double overlapScore = normalizeContextOverlap(task.getDescription());

        return 0.25 * priorityScore
                + 0.20 * deadlineScore
                + 0.20 * costScore
                + 0.15 * confidenceScore
                + 0.20 * overlapScore;
    }

    private double normalizePriority(Integer priority) {
        if (priority == null) {
            return 0.5;
        }
        int clamped = Math.max(1, Math.min(10, priority));
        return (clamped - 1.0) / 9.0;
    }

    private double normalizeDeadline(LocalDateTime deadline) {
        if (deadline == null) {
            return 0.5;
        }
        long minutes = Duration.between(LocalDateTime.now(), deadline).toMinutes();
        if (minutes <= 0) {
            return 1.0;
        }
        return Math.min(1.0, Math.max(0.0, minutes / 1440.0));
    }

    private double normalizeEstimatedCost(java.math.BigDecimal estimatedCostUsd) {
        if (estimatedCostUsd == null) {
            return 0.5;
        }
        return Math.min(1.0, Math.max(0.0, estimatedCostUsd.doubleValue() / 10.0));
    }

    private double normalizeConfidence(Integer confidence) {
        if (confidence == null) {
            return 0.5;
        }
        return Math.min(1.0, Math.max(0.0, confidence / 100.0));
    }

    private double normalizeContextOverlap(String description) {
        if (description == null || description.isBlank()) {
            return 0.5;
        }
        String lower = description.toLowerCase();
        long keywordCount = java.util.Arrays.stream(new String[]{"research", "write", "review", "plan", "product", "market", "brief", "design"})
                .filter(lower::contains)
                .count();
        return Math.min(1.0, keywordCount / 5.0);
    }
}
