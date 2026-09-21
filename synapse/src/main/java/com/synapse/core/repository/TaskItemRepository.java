package com.synapse.core.repository;

import com.synapse.core.entity.TaskItem;
import com.synapse.core.model.TaskItemStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskItemRepository extends JpaRepository<TaskItem, UUID> {
    List<TaskItem> findByGoalId(UUID goalId);
    List<TaskItem> findByOrganizationId(UUID organizationId);
    List<TaskItem> findByStatus(TaskItemStatus status);
    List<TaskItem> findByAssignedAgentId(UUID agentId);
    List<TaskItem> findByOrganizationIdAndStatus(UUID organizationId, TaskItemStatus status);
    Optional<TaskItem> findByIdAndOrganizationId(UUID id, UUID organizationId);
    List<TaskItem> findByParentTaskId(UUID parentTaskId);
}
