package com.synapse.core.service;

import com.synapse.core.entity.Agent;
import com.synapse.core.entity.Goal;
import com.synapse.core.entity.Message;
import com.synapse.core.entity.TaskItem;
import com.synapse.core.model.GoalStatus;
import com.synapse.core.model.TaskItemStatus;
import com.synapse.core.service.TraceContext;
import com.synapse.core.repository.GoalRepository;
import com.synapse.core.repository.TaskItemRepository;
import com.synapse.core.model.ApprovalEffectType;
import com.synapse.governance.ApprovalGate;
import com.synapse.governance.BudgetStatus;
import com.synapse.governance.BudgetTracker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrchestrationService {

    private final DecomposerService decomposerService;
    private final MatcherService matcherService;
    private final DispatcherService dispatcherService;
    private final MessageService messageService;
    private final GoalRepository goalRepository;
    private final TaskItemRepository taskItemRepository;
    private final BudgetTracker budgetTracker;
    private final ApprovalGate approvalGate;

    public Goal executeGoal(Goal goal) {
        TraceContext.setContext(java.util.UUID.randomUUID(), java.util.UUID.randomUUID());
        TraceContext.Context context = TraceContext.getContext();
        goal.setStatus(GoalStatus.IN_PROGRESS);
        goalRepository.save(goal);

        try {
            List<TaskItem> tasks = decomposerService.decomposeGoal(goal);
            goal.setTasks(tasks);

            boolean allTasksCompleted = true;

            for (TaskItem task : tasks) {
                if (task.getOrganization() != null) {
                    BudgetStatus budgetStatus = budgetTracker.checkBudget(
                            task.getOrganization(),
                            task.getEstimatedCostUsd() == null ? BigDecimal.ZERO : task.getEstimatedCostUsd()
                    );
                    if (budgetStatus != BudgetStatus.OK) {
                        approvalGate.evaluate(task, task.getConfidence() == null ? 80 : task.getConfidence(), ApprovalEffectType.SPEND);
                    }
                }

                Agent agent = matcherService.matchAgentForTask(task);

                if (agent == null) {
                    task.setStatus(TaskItemStatus.BLOCKED);
                    taskItemRepository.save(task);
                    allTasksCompleted = false;
                    continue;
                }

                task.setAssignedAgent(agent);
                task.setStatus(TaskItemStatus.ASSIGNED);
                taskItemRepository.save(task);

                messageService.createMessage(
                        context.getRunId(),
                        context.getTraceId(),
                        agent,
                        null,
                        "Matched task: " + task.getDescription(),
                        task.getConfidence()
                );

                try {
                    dispatcherService.dispatchTask(task, agent);
                } catch (RuntimeException ex) {
                    allTasksCompleted = false;
                }
            }

            goal.setStatus(allTasksCompleted ? GoalStatus.COMPLETED : GoalStatus.FAILED);
            return goalRepository.save(goal);
        } finally {
            TraceContext.clear();
        }
    }
}
