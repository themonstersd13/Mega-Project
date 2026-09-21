package com.synapse.core.service;

import com.synapse.core.entity.Agent;
import com.synapse.core.entity.ExecutionLog;
import com.synapse.core.entity.Goal;
import com.synapse.core.entity.Organization;
import com.synapse.core.entity.TaskItem;
import com.synapse.core.model.GoalStatus;
import com.synapse.core.model.TaskItemStatus;
import com.synapse.core.repository.GoalRepository;
import com.synapse.core.repository.OrganizationRepository;
import com.synapse.core.repository.TaskItemRepository;
import com.synapse.governance.ApprovalGate;
import com.synapse.governance.BudgetTracker;
import com.synapse.llm.dto.LlmResponse;
import com.synapse.llm.provider.LlmProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrchestrationServiceTest {

    @Mock
    private DecomposerService decomposerService;

    @Mock
    private MatcherService matcherService;

    @Mock
    private DispatcherService dispatcherService;

    @Mock
    private MessageService messageService;

    @Mock
    private GoalRepository goalRepository;

    @Mock
    private TaskItemRepository taskItemRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    private OrchestrationService orchestrationService;
    private Goal testGoal;
    private Organization testOrg;
    private Agent testAgent;
    private TaskItem testTask;

    @BeforeEach
    void setUp() {
        BudgetTracker budgetTracker = new BudgetTracker(organizationRepository);
        ApprovalGate approvalGate = new ApprovalGate(mock(com.synapse.core.repository.ApprovalRepository.class));
        orchestrationService = new OrchestrationService(
                decomposerService,
                matcherService,
                dispatcherService,
                messageService,
                goalRepository,
                taskItemRepository,
                budgetTracker,
                approvalGate
        );

        testOrg = Organization.builder()
                .id(UUID.randomUUID())
                .name("Test Org")
                .createdAt(Instant.now())
                .build();

        testGoal = Goal.builder()
                .id(UUID.randomUUID())
                .organization(testOrg)
                .description("Complete task")
                .status(GoalStatus.PENDING)
                .createdAt(Instant.now())
                .build();

        testAgent = Agent.builder()
                .id(UUID.randomUUID())
                .build();

        testTask = TaskItem.builder()
                .id(UUID.randomUUID())
                .goal(testGoal)
                .organization(testOrg)
                .description("Task description")
                .status(TaskItemStatus.PENDING)
                .build();
    }

    @Test
    void testExecuteGoalFullFlow() {
        ExecutionLog mockLog = ExecutionLog.builder()
                .id(UUID.randomUUID())
                .task(testTask)
                .succeeded(true)
                .actualTokensUsed(250L)
                .actualCostUsd(BigDecimal.valueOf(0.0008))
                .build();

        when(decomposerService.decomposeGoal(testGoal))
                .thenReturn(java.util.Collections.singletonList(testTask));
        when(matcherService.matchAgentForTask(any(TaskItem.class))).thenReturn(testAgent);
        when(dispatcherService.dispatchTask(any(TaskItem.class), any(Agent.class))).thenReturn(mockLog);
        when(goalRepository.save(any(Goal.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(taskItemRepository.save(any(TaskItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Goal result = orchestrationService.executeGoal(testGoal);

        assertNotNull(result);
        assertEquals(GoalStatus.COMPLETED, result.getStatus());
    }
}
