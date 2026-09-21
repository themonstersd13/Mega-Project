package com.synapse.scheduler;

import com.synapse.core.entity.Organization;
import com.synapse.core.entity.TaskItem;
import com.synapse.core.model.TaskItemStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BatchSchedulerBenchmarkTest {

    @Test
    void benchmarkShouldLowerCostAndCallCount() {
        BenchmarkRunner runner = new BenchmarkRunner();
        BenchmarkResult result = runner.run(25, 5);

        assertTrue(result.batchedApiCalls() < result.naiveApiCalls());
        assertTrue(result.batchedCostUsd().compareTo(result.naiveCostUsd()) < 0);
        assertTrue(result.costReductionPercent().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void dependentTasksShouldNeverBeBatchedTogether() {
        Organization org = new Organization();
        org.setId(UUID.randomUUID());

        TaskItem parent = TaskItem.builder()
                .id(UUID.randomUUID())
                .organization(org)
                .description("Parent task")
                .status(TaskItemStatus.PENDING)
                .priority(5)
                .build();

        TaskItem child = TaskItem.builder()
                .id(UUID.randomUUID())
                .organization(org)
                .description("Child task")
                .status(TaskItemStatus.PENDING)
                .priority(7)
                .parentTask(parent)
                .build();

        BatchCompatibilityChecker checker = new BatchCompatibilityChecker();
        assertFalse(checker.canBatchTogether(parent, child));
    }
}
