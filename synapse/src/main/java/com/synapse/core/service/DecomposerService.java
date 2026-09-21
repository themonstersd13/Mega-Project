package com.synapse.core.service;

import com.synapse.core.entity.Goal;
import com.synapse.core.entity.TaskItem;
import com.synapse.core.model.TaskItemStatus;
import com.synapse.core.repository.TaskItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DecomposerService {

    private final TaskItemRepository taskItemRepository;

    public List<TaskItem> decomposeGoal(Goal goal) {
        String description = goal.getDescription() == null ? "goal" : goal.getDescription();
        TaskItem research = buildTask(goal, description, "Research", 8, new String[]{"research", "analysis"});
        TaskItem draft = buildTask(goal, description, "Draft", 6, new String[]{"writing", "synthesis"});
        TaskItem review = buildTask(goal, description, "Review", 7, new String[]{"review", "quality"});

        return List.of(
                taskItemRepository.save(research),
                taskItemRepository.save(draft),
                taskItemRepository.save(review)
        );
    }

    private TaskItem buildTask(Goal goal, String description, String stage, int priority, String[] skills) {
        return TaskItem.builder()
                .id(UUID.randomUUID())
                .goal(goal)
                .organization(goal.getOrganization())
                .description(stage + " task: " + description)
                .requiredSkillTags(skills)
                .status(TaskItemStatus.PENDING)
                .priority(priority)
                .retryCount(0)
                .build();
    }
}
