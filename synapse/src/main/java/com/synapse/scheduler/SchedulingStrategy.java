package com.synapse.scheduler;

import com.synapse.core.entity.TaskItem;

public interface SchedulingStrategy {
    double score(TaskItem task);
}
