package com.synapse.scheduler;

import com.synapse.core.entity.TaskItem;

import java.util.List;

public record TaskBatch(List<TaskItem> tasks, long createdAtMillis) {
    public static TaskBatch of(List<TaskItem> tasks) {
        return new TaskBatch(List.copyOf(tasks), System.currentTimeMillis());
    }

    public int size() {
        return tasks == null ? 0 : tasks.size();
    }
}
