package com.synapse.core.service;

import com.synapse.core.entity.Agent;
import com.synapse.core.entity.TaskItem;
import com.synapse.core.model.AgentStatus;
import com.synapse.core.repository.AgentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MatcherService {

    private final AgentRepository agentRepository;

    public Agent matchAgentForTask(TaskItem task) {
        if (task.getOrganization() == null || task.getOrganization().getId() == null) {
            return null;
        }

        List<Agent> availableAgents = agentRepository.findByRoleOrganizationIdAndStatus(
                task.getOrganization().getId(),
                AgentStatus.IDLE
        );

        return availableAgents.stream()
                .min(Comparator.comparingInt(agent ->
                        agent.getAssignedTasks() == null ? 0 : agent.getAssignedTasks().size()))
                .orElse(null);
    }
}
