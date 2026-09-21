package com.synapse.scheduler;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record BenchmarkResult(
        int taskCount,
        int naiveApiCalls,
        int batchedApiCalls,
        BigDecimal naiveCostUsd,
        BigDecimal batchedCostUsd,
        double naiveMeanLatencyMs,
        double batchedMeanLatencyMs,
        double naiveP95LatencyMs,
        double batchedP95LatencyMs,
        double batchFillRatio,
        int failedTasks,
        int recoveredTasks
) {
    public BigDecimal costReductionPercent() {
        if (naiveCostUsd == null || naiveCostUsd.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        BigDecimal delta = naiveCostUsd.subtract(batchedCostUsd);
        return delta.multiply(BigDecimal.valueOf(100))
                .divide(naiveCostUsd, 4, RoundingMode.HALF_UP);
    }

    public String formatSummary() {
        return "Task count=" + taskCount
                + ", naiveApiCalls=" + naiveApiCalls
                + ", batchedApiCalls=" + batchedApiCalls
                + ", naiveCost=" + naiveCostUsd
                + ", batchedCost=" + batchedCostUsd
                + ", costReduction=" + costReductionPercent() + "%";
    }
}
