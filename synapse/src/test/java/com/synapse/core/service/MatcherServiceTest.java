package com.synapse.core.service;

import com.synapse.core.entity.Agent;
import com.synapse.core.entity.TaskItem;
import com.synapse.core.entity.Organization;
import com.synapse.core.model.AgentStatus;
import com.synapse.core.repository.AgentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatcherServiceTest {

    @Mock
    private AgentRepository agentRepository;

    @InjectMocks
    private MatcherService matcherService;

    private TaskItem testTask;
    private Agent agent1;
    private Agent agent2;
    private Organization testOrg;

    @BeforeEach
    void setUp() {
        testOrg = Organization.builder()
                .id(UUID.randomUUID())
                .name("Test Org")
                .createdAt(Instant.now())
                .build();

        testTask = TaskItem.builder()
                .id(UUID.randomUUID())
                .organization(testOrg)
                .description("Test task")
                .build();

        agent1 = Agent.builder()
                .id(UUID.randomUUID())
                .status(AgentStatus.IDLE)
                .build();

        agent2 = Agent.builder()
                .id(UUID.randomUUID())
                .status(AgentStatus.IDLE)
                .build();
    }

    @Test
    void testMatchAgentForTaskWithAvailableAgents() {
        when(agentRepository.findByRoleOrganizationIdAndStatus(testOrg.getId(), AgentStatus.IDLE))
                .thenReturn(Arrays.asList(agent1, agent2));

        Agent matched = matcherService.matchAgentForTask(testTask);

        assertNotNull(matched);
        assertEquals(AgentStatus.IDLE, matched.getStatus());
    }

    @Test
    void testMatchAgentForTaskNoAvailableAgents() {
        when(agentRepository.findByRoleOrganizationIdAndStatus(testOrg.getId(), AgentStatus.IDLE))
                .thenReturn(Collections.emptyList());

        Agent matched = matcherService.matchAgentForTask(testTask);

        assertNull(matched);
    }

    @Test
    void testMatchAgentSelectsAgentWithFewestTasks() {
        agent1.setAssignedTasks(Arrays.asList(new TaskItem(), new TaskItem())); // 2 tasks
        agent2.setAssignedTasks(Collections.singletonList(new TaskItem())); // 1 task

        when(agentRepository.findByRoleOrganizationIdAndStatus(testOrg.getId(), AgentStatus.IDLE))
                .thenReturn(Arrays.asList(agent1, agent2));

        Agent matched = matcherService.matchAgentForTask(testTask);

        assertNotNull(matched);
        assertEquals(agent2.getId(), matched.getId());
    }
}
