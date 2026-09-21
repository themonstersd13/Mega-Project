package com.synapse.demo;

import com.synapse.analytics.CostPredictionService;
import com.synapse.api.response.CostPredictionResponse;
import com.synapse.core.entity.Agent;
import com.synapse.core.entity.Goal;
import com.synapse.core.entity.MemoryEntry;
import com.synapse.core.entity.Organization;
import com.synapse.core.entity.Role;
import com.synapse.core.entity.TaskItem;
import com.synapse.core.entity.Team;
import com.synapse.core.model.AgentStatus;
import com.synapse.core.model.GoalStatus;
import com.synapse.core.model.MemoryScope;
import com.synapse.core.model.ModelTier;
import com.synapse.core.model.TaskItemStatus;
import com.synapse.core.model.TeamTopology;
import com.synapse.core.repository.AgentRepository;
import com.synapse.core.repository.GoalRepository;
import com.synapse.core.repository.OrganizationRepository;
import com.synapse.core.repository.RoleRepository;
import com.synapse.core.repository.TaskItemRepository;
import com.synapse.core.repository.TeamRepository;
import com.synapse.governance.ApprovalDecision;
import com.synapse.governance.ApprovalGate;
import com.synapse.governance.ApprovalResult;
import com.synapse.governance.BudgetTracker;
import com.synapse.governance.BudgetStatus;
import com.synapse.memory.MemoryService;
import com.synapse.scheduler.BenchmarkRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoServiceTest {

    @Mock private OrganizationRepository organizationRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private AgentRepository agentRepository;
    @Mock private GoalRepository goalRepository;
    @Mock private TaskItemRepository taskItemRepository;
    @Mock private MemoryService memoryService;
    @Mock private BudgetTracker budgetTracker;
    @Mock private ApprovalGate approvalGate;
    @Mock private CostPredictionService costPredictionService;

    @Test
    void evaluateDemoBuildsSeededReport() {
        Organization organization = Organization.builder()
                .id(UUID.randomUUID())
                .name("AI Product Team")
                .totalBudgetUsd(new BigDecimal("250.00"))
                .spentBudgetUsd(BigDecimal.ZERO)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Role role = Role.builder()
                .id(UUID.randomUUID())
                .organization(organization)
                .name("Researcher")
                .modelTier(ModelTier.HAIKU)
                .tokenBudget(45000L)
                .build();

        Team team = Team.builder()
                .id(UUID.randomUUID())
                .organization(organization)
                .name("Core Team")
                .topology(TeamTopology.HIERARCHICAL)
                .build();

        Goal goal = Goal.builder()
                .id(UUID.randomUUID())
                .organization(organization)
                .description("Research user pain points for batching and explainability.")
                .status(GoalStatus.PENDING)
                .build();

        TaskItem task = TaskItem.builder()
                .id(UUID.randomUUID())
                .goal(goal)
                .organization(organization)
                .description("Research user pain points for batching and explainability.")
                .status(TaskItemStatus.PENDING)
                .build();

        MemoryEntry memoryEntry = MemoryEntry.builder()
                .id(UUID.randomUUID())
                .organization(organization)
                .content("Batching reduced cost and call count in the SYNAPSE benchmark.")
                .scope(MemoryScope.ORG)
                .build();

        when(organizationRepository.findByName(anyString())).thenReturn(Optional.empty());
        when(organizationRepository.save(any(Organization.class))).thenReturn(organization);
        when(organizationRepository.findById(any(UUID.class))).thenReturn(Optional.of(organization));

        when(roleRepository.findByNameAndOrganizationId(anyString(), any(UUID.class))).thenReturn(Optional.empty());
        when(roleRepository.save(any(Role.class))).thenReturn(role);
        when(roleRepository.findByOrganizationId(any(UUID.class))).thenReturn(List.of(role, role, role));

        when(teamRepository.findByNameAndOrganizationId(anyString(), any(UUID.class))).thenReturn(Optional.empty());
        when(teamRepository.save(any(Team.class))).thenReturn(team);

        when(agentRepository.findByRoleId(any(UUID.class))).thenReturn(List.of());
        when(agentRepository.save(any(Agent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(agentRepository.findByRoleOrganizationIdAndStatus(any(UUID.class), eq(AgentStatus.IDLE)))
                .thenReturn(List.of(
                        Agent.builder().id(UUID.randomUUID()).role(role).team(team).status(AgentStatus.IDLE).build(),
                        Agent.builder().id(UUID.randomUUID()).role(role).team(team).status(AgentStatus.IDLE).build(),
                        Agent.builder().id(UUID.randomUUID()).role(role).team(team).status(AgentStatus.IDLE).build()
                ));

        when(goalRepository.findByOrganizationId(any(UUID.class))).thenReturn(List.of(goal, goal, goal));
        when(taskItemRepository.findByOrganizationId(any(UUID.class))).thenReturn(List.of(task));

        when(memoryService.recall(anyString(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(), List.of(memoryEntry));
        when(memoryService.store(anyString(), any(), any(), any(), any(), anyInt()))
                .thenReturn(memoryEntry);
        when(memoryService.relate(any(UUID.class), any(UUID.class), any()))
                .thenReturn(null);

        when(budgetTracker.checkBudget(any(), any())).thenReturn(BudgetStatus.OK);
        when(approvalGate.evaluate(any(TaskItem.class), anyInt(), any()))
                .thenReturn(ApprovalResult.required(UUID.randomUUID()));
        when(approvalGate.resolve(any(UUID.class), eq(ApprovalDecision.APPROVED), anyString()))
                .thenReturn(com.synapse.core.entity.Approval.builder()
                        .id(UUID.randomUUID())
                        .task(task)
                        .status(com.synapse.core.model.ApprovalStatus.APPROVED)
                        .build());

        when(costPredictionService.predict(any()))
                .thenReturn(CostPredictionResponse.builder()
                        .modelName("linear_regression")
                        .predictedCostUsd(0.0123)
                        .mae(0.001)
                        .rmse(0.002)
                        .rSquared(0.91)
                        .trainingRows(220)
                        .fallbackUsed(false)
                        .build());

        DemoService service = new DemoService(
                organizationRepository,
                roleRepository,
                teamRepository,
                agentRepository,
                goalRepository,
                taskItemRepository,
                memoryService,
                budgetTracker,
                approvalGate,
                new BenchmarkRunner(),
                costPredictionService
        );

        DemoService.DemoEvaluationResponse response = service.evaluateDemo(5);

        assertNotNull(response);
        assertEquals(7, response.criteria().size());
        assertTrue(response.reportMarkdown().contains("SYNAPSE Demo Report"));
        assertTrue(response.benchmark().meanBatchedCostUsd() < response.benchmark().meanNaiveCostUsd());
        assertTrue(response.prediction().getPredictedCostUsd() > 0.0);
    }
}
