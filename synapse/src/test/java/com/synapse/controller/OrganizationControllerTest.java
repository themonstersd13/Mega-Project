package com.synapse.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.api.request.CreateGoalRequest;
import com.synapse.api.request.CreateOrganizationRequest;
import com.synapse.api.request.CreateRoleRequest;
import com.synapse.api.request.CreateTeamRequest;
import com.synapse.core.entity.Organization;
import com.synapse.core.entity.Goal;
import com.synapse.core.entity.Role;
import com.synapse.core.entity.Team;
import com.synapse.core.model.GoalStatus;
import com.synapse.core.model.ModelTier;
import com.synapse.core.model.TeamTopology;
import com.synapse.core.repository.ExecutionLogRepository;
import com.synapse.core.repository.GoalRepository;
import com.synapse.core.repository.OrganizationRepository;
import com.synapse.core.repository.RoleRepository;
import com.synapse.core.repository.RoleResponsibilityRepository;
import com.synapse.core.repository.TaskItemRepository;
import com.synapse.core.repository.TeamRepository;
import com.synapse.core.service.OrchestrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
class OrganizationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrganizationRepository organizationRepository;

    @MockBean
    private RoleRepository roleRepository;

    @MockBean
    private RoleResponsibilityRepository roleResponsibilityRepository;

    @MockBean
    private TeamRepository teamRepository;

    @MockBean
    private GoalRepository goalRepository;

    @MockBean
    private TaskItemRepository taskItemRepository;

    @MockBean
    private ExecutionLogRepository executionLogRepository;

    @MockBean
    private OrchestrationService orchestrationService;

    private Organization testOrg;
    private CreateOrganizationRequest createOrgRequest;

    @BeforeEach
    void setUp() {
        testOrg = Organization.builder()
                .id(UUID.randomUUID())
                .name("Test Organization")
                .totalBudgetUsd(BigDecimal.valueOf(10000))
                .spentBudgetUsd(BigDecimal.ZERO)
                .createdAt(Instant.now())
                .build();

        createOrgRequest = CreateOrganizationRequest.builder()
                .name("Test Organization")
                .description("Test Desc")
                .totalBudgetUsd(BigDecimal.valueOf(10000))
                .build();
    }

    @Test
    void testCreateOrganization() throws Exception {
        when(organizationRepository.findByName("Test Organization")).thenReturn(Optional.empty());
        when(organizationRepository.save(any(Organization.class))).thenReturn(testOrg);

        mockMvc.perform(post("/organizations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createOrgRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Test Organization"));
    }

    @Test
    void testCreateOrganizationDuplicate() throws Exception {
        when(organizationRepository.findByName("Test Organization")).thenReturn(Optional.of(testOrg));

        mockMvc.perform(post("/organizations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createOrgRequest)))
                .andExpect(status().isConflict());
    }

    @Test
    void testCreateRole() throws Exception {
        UUID orgId = testOrg.getId();
        Role role = Role.builder()
                .id(UUID.randomUUID())
                .organization(testOrg)
                .name("Test Role")
                .modelTier(ModelTier.ADVANCED)
                .build();

        CreateRoleRequest roleRequest = CreateRoleRequest.builder()
                .name("Test Role")
                .responsibilities(Collections.singletonList("Testing"))
                .modelTier(ModelTier.ADVANCED)
                .build();

        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(testOrg));
        when(roleRepository.save(any(Role.class))).thenReturn(role);
        when(roleResponsibilityRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/organizations/" + orgId + "/roles")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(roleRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Test Role"));
    }

    @Test
    void testCreateTeam() throws Exception {
        UUID orgId = testOrg.getId();
        Team team = Team.builder()
                .id(UUID.randomUUID())
                .organization(testOrg)
                .name("Test Team")
                .topology(TeamTopology.FLAT)
                .build();

        CreateTeamRequest teamRequest = CreateTeamRequest.builder()
                .name("Test Team")
                .teamTopology(TeamTopology.FLAT)
                .build();

        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(testOrg));
        when(teamRepository.save(any(Team.class))).thenReturn(team);

        mockMvc.perform(post("/organizations/" + orgId + "/teams")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(teamRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Test Team"));
    }

    @Test
    void testCreateGoal() throws Exception {
        UUID orgId = testOrg.getId();
        Goal goal = Goal.builder()
                .id(UUID.randomUUID())
                .organization(testOrg)
                .description("Complete task")
                .status(GoalStatus.COMPLETED)
                .createdAt(Instant.now())
                .build();

        CreateGoalRequest goalRequest = CreateGoalRequest.builder()
                .description("Complete task")
                .build();

        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(testOrg));
        when(goalRepository.save(any(Goal.class))).thenReturn(goal);
        when(orchestrationService.executeGoal(any(Goal.class))).thenReturn(goal);
        when(goalRepository.findById(goal.getId())).thenReturn(Optional.of(goal));

        mockMvc.perform(post("/organizations/" + orgId + "/goals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(goalRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("Complete task"));
    }

    @Test
    void testGetGoal() throws Exception {
        UUID orgId = testOrg.getId();
        Goal goal = Goal.builder()
                .id(UUID.randomUUID())
                .organization(testOrg)
                .description("Test Goal")
                .status(GoalStatus.COMPLETED)
                .tasks(Collections.emptyList())
                .createdAt(Instant.now())
                .build();

        when(goalRepository.findByIdAndOrganizationId(goal.getId(), orgId)).thenReturn(Optional.of(goal));

        mockMvc.perform(get("/organizations/" + orgId + "/goals/" + goal.getId())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Test Goal"));
    }
}
