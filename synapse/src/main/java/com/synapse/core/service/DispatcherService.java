package com.synapse.core.service;

import com.synapse.core.entity.Agent;
import com.synapse.core.entity.ExecutionLog;
import com.synapse.core.entity.TaskItem;
import com.synapse.core.model.TaskItemStatus;
import com.synapse.core.repository.ExecutionLogRepository;
import com.synapse.core.repository.TaskItemRepository;
import com.synapse.llm.dto.LlmResponse;
import com.synapse.llm.provider.LlmProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DispatcherService {

    private final LlmProvider llmProvider;
    private final ExecutionLogRepository executionLogRepository;
    private final TaskItemRepository taskItemRepository;
    private final MessageService messageService;

    public ExecutionLog dispatchTask(TaskItem task, Agent agent) {
        Instant startedAt = Instant.now();
        task.setAssignedAgent(agent);
        task.setStatus(TaskItemStatus.IN_PROGRESS);
        TraceContext.Context context = TraceContext.getContext();

        try {
            LlmResponse response = llmProvider.execute(buildPrompt(task, agent));
            long latencyMs = Duration.between(startedAt, Instant.now()).toMillis();
            long totalTokens = safeLong(response.getInputTokens()) + safeLong(response.getOutputTokens());

            task.setStatus(TaskItemStatus.COMPLETED);
            task.setActualCostUsd(response.getCostUsd());
            task.setActualLatencyMs(latencyMs);
            taskItemRepository.save(task);

            messageService.createMessage(
                    context.getRunId(),
                    context.getTraceId(),
                    agent,
                    null,
                    "Completed task: " + task.getDescription(),
                    task.getConfidence()
            );

            ExecutionLog log = ExecutionLog.builder()
                    .id(UUID.randomUUID())
                    .task(task)
                    .roleName(agent != null && agent.getRole() != null ? agent.getRole().getName() : null)
                    .requiredSkillTagCount(task.getRequiredSkillTags() == null ? 0 : task.getRequiredSkillTags().length)
                    .descriptionLength(task.getDescription() == null ? 0 : task.getDescription().length())
                    .actualTokensUsed(totalTokens)
                    .actualCostUsd(response.getCostUsd())
                    .actualLatencyMs(latencyMs)
                    .priority(task.getPriority())
                    .retryCount(task.getRetryCount())
                    .succeeded(true)
                    .humanApprovalRequired(false)
                    .wasEscalated(false)
                    .build();

            return executionLogRepository.save(log);
        } catch (RuntimeException ex) {
            long latencyMs = Duration.between(startedAt, Instant.now()).toMillis();

            task.setStatus(TaskItemStatus.FAILED);
            task.setActualLatencyMs(latencyMs);
            taskItemRepository.save(task);

            messageService.createMessage(
                    context.getRunId(),
                    context.getTraceId(),
                    agent,
                    null,
                    "Failed task: " + task.getDescription(),
                    task.getConfidence()
            );

            ExecutionLog failedLog = ExecutionLog.builder()
                    .id(UUID.randomUUID())
                    .task(task)
                    .roleName(agent != null && agent.getRole() != null ? agent.getRole().getName() : null)
                    .requiredSkillTagCount(task.getRequiredSkillTags() == null ? 0 : task.getRequiredSkillTags().length)
                    .descriptionLength(task.getDescription() == null ? 0 : task.getDescription().length())
                    .actualLatencyMs(latencyMs)
                    .priority(task.getPriority())
                    .retryCount(task.getRetryCount())
                    .succeeded(false)
                    .humanApprovalRequired(false)
                    .wasEscalated(false)
                    .build();

            executionLogRepository.save(failedLog);
            throw ex;
        }
    }

    private String buildPrompt(TaskItem task, Agent agent) {
        if (agent != null && agent.getRole() != null && agent.getRole().getSystemPrompt() != null) {
            return agent.getRole().getSystemPrompt() + System.lineSeparator() + System.lineSeparator() + task.getDescription();
        }

        return task.getDescription();
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }
}
