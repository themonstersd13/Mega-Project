package com.synapse.core.repository;

import com.synapse.core.entity.Agent;
import com.synapse.core.model.AgentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgentRepository extends JpaRepository<Agent, UUID> {
    List<Agent> findByRoleId(UUID roleId);
    List<Agent> findByTeamId(UUID teamId);
    List<Agent> findByStatus(AgentStatus status);
    List<Agent> findByStatusAndTeamId(AgentStatus status, UUID teamId);
    List<Agent> findByAssignedTasksId(UUID taskId);
    Optional<Agent> findByIdAndRoleId(UUID id, UUID roleId);
    List<Agent> findByRoleOrganizationIdAndStatus(UUID organizationId, AgentStatus status);
}
