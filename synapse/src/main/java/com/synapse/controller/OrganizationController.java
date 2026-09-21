package com.synapse.controller;

import com.synapse.api.request.CreateGoalRequest;
import com.synapse.api.request.CreateOrganizationRequest;
import com.synapse.api.request.CreateRoleRequest;
import com.synapse.api.request.CreateTeamRequest;
import com.synapse.api.response.GoalResponse;
import com.synapse.api.response.OrganizationResponse;
import com.synapse.api.response.RoleResponse;
import com.synapse.api.response.TaskItemResponse;
import com.synapse.api.response.TeamResponse;
import com.synapse.core.entity.Goal;
import com.synapse.core.entity.Organization;
import com.synapse.core.entity.Role;
import com.synapse.core.entity.RoleResponsibility;
import com.synapse.core.entity.Team;
import com.synapse.core.model.GoalStatus;
import com.synapse.core.repository.GoalRepository;
import com.synapse.core.repository.OrganizationRepository;
import com.synapse.core.repository.RoleRepository;
import com.synapse.core.repository.RoleResponsibilityRepository;
import com.synapse.core.repository.TeamRepository;
import com.synapse.core.service.OrchestrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/organizations")
@RequiredArgsConstructor
public class OrganizationController {
    private final OrganizationRepository organizationRepository;
    private final RoleRepository roleRepository;
    private final RoleResponsibilityRepository roleResponsibilityRepository;
    private final TeamRepository teamRepository;
    private final GoalRepository goalRepository;
    private final OrchestrationService orchestrationService;

    @PostMapping
    public ResponseEntity<OrganizationResponse> createOrganization(@RequestBody CreateOrganizationRequest request) {
        if (organizationRepository.findByName(request.getName()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        Organization org = Organization.builder()
                .id(UUID.randomUUID())
                .name(request.getName())
                .totalBudgetUsd(request.getTotalBudgetUsd())
                .spentBudgetUsd(java.math.BigDecimal.ZERO)
                .build();

        Organization saved = organizationRepository.save(org);
        
        OrganizationResponse response = OrganizationResponse.builder()
                .id(saved.getId())
                .name(saved.getName())
                .totalBudgetUsd(saved.getTotalBudgetUsd())
                .spentBudgetUsd(saved.getSpentBudgetUsd())
                .createdAt(saved.getCreatedAt())
                .updatedAt(saved.getUpdatedAt())
                .build();
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/roles")
    public ResponseEntity<RoleResponse> createRole(
            @PathVariable UUID id,
            @RequestBody CreateRoleRequest request) {
        Organization org = organizationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        Role role = Role.builder()
                .id(UUID.randomUUID())
                .organization(org)
                .name(request.getName())
                .systemPrompt(request.getSystemPrompt())
                .modelTier(request.getModelTier())
                .tokenBudget(request.getTokenBudget())
                .build();

        Role savedRole = roleRepository.save(role);

        if (request.getResponsibilities() != null) {
            for (String resp : request.getResponsibilities()) {
                RoleResponsibility responsibility = RoleResponsibility.builder()
                        .id(UUID.randomUUID())
                        .role(savedRole)
                        .responsibility(resp)
                        .build();
                roleResponsibilityRepository.save(responsibility);
            }
        }

        RoleResponse response = RoleResponse.builder()
                .id(savedRole.getId())
                .name(savedRole.getName())
                .systemPrompt(savedRole.getSystemPrompt())
                .modelTier(savedRole.getModelTier())
                .tokenBudget(savedRole.getTokenBudget())
                .responsibilities(request.getResponsibilities())
                .createdAt(savedRole.getCreatedAt())
                .updatedAt(savedRole.getUpdatedAt())
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/teams")
    public ResponseEntity<TeamResponse> createTeam(
            @PathVariable UUID id,
            @RequestBody CreateTeamRequest request) {
        Organization org = organizationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        Team team = Team.builder()
                .id(UUID.randomUUID())
                .organization(org)
                .name(request.getName())
                .topology(request.getTeamTopology())
                .build();

        Team savedTeam = teamRepository.save(team);
        
        TeamResponse response = TeamResponse.builder()
                .id(savedTeam.getId())
                .name(savedTeam.getName())
                .topology(savedTeam.getTopology())
                .createdAt(savedTeam.getCreatedAt())
                .updatedAt(savedTeam.getUpdatedAt())
                .build();
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/goals")
    public ResponseEntity<GoalResponse> createGoal(
            @PathVariable UUID id,
            @RequestBody CreateGoalRequest request) {
        Organization org = organizationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        Goal goal = Goal.builder()
                .id(UUID.randomUUID())
                .organization(org)
                .description(request.getDescription())
                .status(GoalStatus.PENDING)
                .build();

        Goal savedGoal = goalRepository.save(goal);

        Goal orchestratedGoal = orchestrationService.executeGoal(savedGoal);
        GoalResponse response = toGoalResponse(orchestratedGoal);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}/goals/{goalId}")
    public ResponseEntity<GoalResponse> getGoal(
            @PathVariable UUID id,
            @PathVariable UUID goalId) {
        Goal goal = goalRepository.findByIdAndOrganizationId(goalId, id)
                .orElseThrow(() -> new RuntimeException("Goal not found"));

        return ResponseEntity.ok(toGoalResponse(goal));
    }

    private GoalResponse toGoalResponse(Goal goal) {
        return GoalResponse.builder()
                .id(goal.getId())
                .description(goal.getDescription())
                .status(goal.getStatus())
                .tasks(goal.getTasks() == null
                        ? java.util.Collections.emptyList()
                        : goal.getTasks().stream().map(task -> TaskItemResponse.builder()
                                .id(task.getId())
                                .description(task.getDescription())
                                .status(task.getStatus())
                                .assignedAgentId(task.getAssignedAgent() == null ? null : task.getAssignedAgent().getId())
                                .actualCostUsd(task.getActualCostUsd())
                                .actualLatencyMs(task.getActualLatencyMs())
                                .createdAt(task.getCreatedAt())
                                .updatedAt(task.getUpdatedAt())
                                .build())
                        .collect(Collectors.toList()))
                .createdAt(goal.getCreatedAt())
                .updatedAt(goal.getUpdatedAt())
                .build();
    }
}

