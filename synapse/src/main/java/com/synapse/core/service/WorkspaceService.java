package com.synapse.core.service;

import com.synapse.api.response.*;
import com.synapse.core.entity.*;
import com.synapse.core.model.AgentStatus;
import com.synapse.core.model.ApprovalStatus;
import com.synapse.core.model.MemoryScope;
import com.synapse.core.model.TaskItemStatus;
import com.synapse.core.repository.*;
import com.synapse.governance.BudgetStatus;
import com.synapse.governance.BudgetTracker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkspaceService {
    private final OrganizationRepository organizationRepository;
    private final RoleRepository roleRepository;
    private final TeamRepository teamRepository;
    private final AgentRepository agentRepository;
    private final GoalRepository goalRepository;
    private final TaskItemRepository taskItemRepository;
    private final ApprovalRepository approvalRepository;
    private final MessageRepository messageRepository;
    private final MemoryEntryRepository memoryEntryRepository;
    private final BudgetTracker budgetTracker;

    public List<Map<String, Object>> listWorkspaces() {
        return organizationRepository.findAll().stream()
                .map(this::toSummaryMap)
                .collect(Collectors.toList());
    }

    public Map<String, Object> snapshot(UUID organizationId) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + organizationId));

        List<RoleResponse> roles = roleRepository.findByOrganizationId(organizationId).stream()
                .map(this::toRoleResponse)
                .collect(Collectors.toList());
        List<TeamResponse> teams = teamRepository.findByOrganizationId(organizationId).stream()
                .map(this::toTeamResponse)
                .collect(Collectors.toList());
        List<AgentResponse> agents = loadAgents(organizationId);
        List<GoalResponse> goals = loadGoals(organizationId);
        List<TaskItemResponse> tasks = loadTasks(organizationId);
        List<MessageResponse> messages = loadMessages(organizationId);
        List<MemoryEntryResponse> memories = loadMemories(organizationId);
        List<ApprovalViewResponse> approvals = loadApprovals(organizationId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", toSummaryMap(organization));
        payload.put("roles", roles);
        payload.put("teams", teams);
        payload.put("agents", agents);
        payload.put("goals", goals);
        payload.put("tasks", tasks);
        payload.put("messages", messages);
        payload.put("memories", memories);
        payload.put("approvals", approvals);
        payload.put("board", board(organizationId));
        return payload;
    }

    public Map<String, Object> board(UUID organizationId) {
        List<TaskItemResponse> tasks = loadTasks(organizationId);
        Map<String, List<TaskItemResponse>> lanes = new LinkedHashMap<>();
        lanes.put(TaskItemStatus.PENDING.name(), new ArrayList<>());
        lanes.put(TaskItemStatus.ASSIGNED.name(), new ArrayList<>());
        lanes.put(TaskItemStatus.IN_PROGRESS.name(), new ArrayList<>());
        lanes.put(TaskItemStatus.COMPLETED.name(), new ArrayList<>());
        lanes.put(TaskItemStatus.FAILED.name(), new ArrayList<>());
        lanes.put(TaskItemStatus.BLOCKED.name(), new ArrayList<>());

        for (TaskItemResponse task : tasks) {
            String laneKey = task.getStatus() == null ? TaskItemStatus.PENDING.name() : task.getStatus().name();
            if (!lanes.containsKey(laneKey)) {
                laneKey = TaskItemStatus.PENDING.name();
            }
            lanes.get(laneKey).add(task);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", toSummaryMap(organizationRepository.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + organizationId))));
        payload.put("lanes", lanes);
        payload.put("agents", loadAgents(organizationId));
        return payload;
    }

    public Map<String, Object> context(UUID organizationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messages", loadMessages(organizationId));
        payload.put("memories", loadMemories(organizationId));
        payload.put("approvals", loadApprovals(organizationId));
        return payload;
    }

    @Transactional
    public TaskItemResponse updateTask(UUID taskId, com.synapse.api.request.TaskUpdateRequest request) {
        TaskItem task = taskItemRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        if (request.getStatus() != null) {
            task.setStatus(request.getStatus());
        }

        if (request.getAssignedAgentId() != null) {
            Agent agent = agentRepository.findById(request.getAssignedAgentId())
                    .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + request.getAssignedAgentId()));
            task.setAssignedAgent(agent);
        }

        TaskItem saved = taskItemRepository.save(task);
        return toTaskResponse(saved);
    }

    private Map<String, Object> toSummaryMap(Organization organization) {
        List<TaskItem> tasks = taskItemRepository.findByOrganizationId(organization.getId());
        long completed = tasks.stream().filter(task -> task.getStatus() == TaskItemStatus.COMPLETED).count();
        long blocked = tasks.stream().filter(task -> task.getStatus() == TaskItemStatus.BLOCKED).count();
        long active = tasks.stream().filter(task -> task.getStatus() == TaskItemStatus.IN_PROGRESS || task.getStatus() == TaskItemStatus.ASSIGNED).count();
        BudgetStatus budgetStatus = budgetTracker.checkBudget(organization, BigDecimal.ZERO);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("organizationId", organization.getId());
        summary.put("organizationName", organization.getName());
        summary.put("totalBudgetUsd", organization.getTotalBudgetUsd());
        summary.put("spentBudgetUsd", organization.getSpentBudgetUsd());
        summary.put("budgetStatus", budgetStatus.name());
        summary.put("roleCount", roleRepository.findByOrganizationId(organization.getId()).size());
        summary.put("teamCount", teamRepository.findByOrganizationId(organization.getId()).size());
        summary.put("agentCount", loadAgents(organization.getId()).size());
        summary.put("goalCount", goalRepository.findByOrganizationId(organization.getId()).size());
        summary.put("taskCount", tasks.size());
        summary.put("completedTaskCount", completed);
        summary.put("activeTaskCount", active);
        summary.put("blockedTaskCount", blocked);
        return summary;
    }

    private List<RoleResponse> loadRoles(UUID organizationId) {
        return roleRepository.findByOrganizationId(organizationId).stream()
                .map(this::toRoleResponse)
                .collect(Collectors.toList());
    }

    private List<TeamResponse> loadTeams(UUID organizationId) {
        return teamRepository.findByOrganizationId(organizationId).stream()
                .map(this::toTeamResponse)
                .collect(Collectors.toList());
    }

    private List<AgentResponse> loadAgents(UUID organizationId) {
        return agentRepository.findAll().stream()
                .filter(agent -> agent.getRole() != null
                        && agent.getRole().getOrganization() != null
                        && organizationId.equals(agent.getRole().getOrganization().getId()))
                .map(agent -> {
                    long activeTaskCount = taskItemRepository.findByOrganizationId(organizationId).stream()
                            .filter(task -> task.getAssignedAgent() != null && agent.getId().equals(task.getAssignedAgent().getId()))
                            .count();
                    return new AgentResponse(
                            agent.getId(),
                            agent.getRole() == null ? "Agent" : agent.getRole().getName(),
                            agent.getRole() == null || agent.getRole().getId() == null ? null : agent.getRole().getId(),
                            agent.getTeam() == null ? null : agent.getTeam().getName(),
                            agent.getTeam() == null ? null : agent.getTeam().getId(),
                            agent.getStatus(),
                            agent.getTokensUsed(),
                            agent.getLastActiveAt(),
                            activeTaskCount
                    );
                })
                .sorted(Comparator.comparing(AgentResponse::roleName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .collect(Collectors.toList());
    }

    private List<GoalResponse> loadGoals(UUID organizationId) {
        return goalRepository.findByOrganizationId(organizationId).stream()
                .map(goal -> GoalResponse.builder()
                        .id(goal.getId())
                        .description(goal.getDescription())
                        .status(goal.getStatus())
                        .tasks(taskItemRepository.findByGoalId(goal.getId()).stream()
                                .map(this::toTaskResponse)
                                .collect(Collectors.toList()))
                        .createdAt(goal.getCreatedAt())
                        .updatedAt(goal.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    private List<TaskItemResponse> loadTasks(UUID organizationId) {
        return taskItemRepository.findByOrganizationId(organizationId).stream()
                .map(this::toTaskResponse)
                .collect(Collectors.toList());
    }

    private List<MessageResponse> loadMessages(UUID organizationId) {
        return messageRepository.findAll().stream()
                .filter(message -> message.getSenderAgent() != null
                        && message.getSenderAgent().getRole() != null
                        && message.getSenderAgent().getRole().getOrganization() != null
                        && organizationId.equals(message.getSenderAgent().getRole().getOrganization().getId()))
                .sorted(Comparator.comparing(Message::getSentAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(20)
                .map(message -> MessageResponse.builder()
                        .id(message.getId())
                        .runId(message.getRunId())
                        .traceId(message.getTraceId())
                        .parentMessageId(message.getParentMessage() == null ? null : message.getParentMessage().getId())
                        .senderAgentId(message.getSenderAgent() == null ? null : message.getSenderAgent().getId())
                        .recipientAgentId(message.getRecipientAgent() == null ? null : message.getRecipientAgent().getId())
                        .content(message.getContent())
                        .confidence(message.getConfidence())
                        .sentAt(message.getSentAt())
                        .build())
                .collect(Collectors.toList());
    }

    private List<MemoryEntryResponse> loadMemories(UUID organizationId) {
        return memoryEntryRepository.findByOrganizationId(organizationId).stream()
                .sorted(Comparator.comparing(MemoryEntry::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(20)
                .map(memory -> new MemoryEntryResponse(
                        memory.getId(),
                        memory.getOrganization() == null ? null : memory.getOrganization().getId(),
                        memory.getAgent() == null ? null : memory.getAgent().getId(),
                        memory.getTask() == null ? null : memory.getTask().getId(),
                        memory.getContent(),
                        memory.getScope(),
                        memory.getConfidence(),
                        memory.getSuperseded(),
                        memory.getCreatedAt()
                ))
                .collect(Collectors.toList());
    }

    private List<ApprovalViewResponse> loadApprovals(UUID organizationId) {
        return approvalRepository.findAll().stream()
                .filter(approval -> approval.getTask() != null
                        && approval.getTask().getOrganization() != null
                        && organizationId.equals(approval.getTask().getOrganization().getId()))
                .sorted(Comparator.comparing(Approval::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(20)
                .map(approval -> new ApprovalViewResponse(
                        approval.getId(),
                        approval.getTask() == null ? null : approval.getTask().getId(),
                        approval.getActionDescription(),
                        approval.getEffectType(),
                        approval.getConfidence(),
                        approval.getStatus(),
                        approval.getResolvedBy(),
                        approval.getResolvedAt(),
                        approval.getCreatedAt()
                ))
                .collect(Collectors.toList());
    }

    private TaskItemResponse toTaskResponse(TaskItem task) {
        return TaskItemResponse.builder()
                .id(task.getId())
                .goalId(task.getGoal() == null ? null : task.getGoal().getId())
                .organizationId(task.getOrganization() == null ? null : task.getOrganization().getId())
                .parentTaskId(task.getParentTask() == null ? null : task.getParentTask().getId())
                .description(task.getDescription())
                .status(task.getStatus())
                .assignedAgentId(task.getAssignedAgent() == null ? null : task.getAssignedAgent().getId())
                .assignedAgentName(task.getAssignedAgent() == null || task.getAssignedAgent().getRole() == null ? null : task.getAssignedAgent().getRole().getName())
                .priority(task.getPriority())
                .confidence(task.getConfidence())
                .requiredSkillTags(task.getRequiredSkillTags() == null ? List.of() : Arrays.asList(task.getRequiredSkillTags()))
                .estimatedCostUsd(task.getEstimatedCostUsd())
                .actualCostUsd(task.getActualCostUsd())
                .actualLatencyMs(task.getActualLatencyMs())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }

    private RoleResponse toRoleResponse(Role role) {
        return RoleResponse.builder()
                .id(role.getId())
                .name(role.getName())
                .systemPrompt(role.getSystemPrompt())
                .modelTier(role.getModelTier())
                .tokenBudget(role.getTokenBudget())
                .responsibilities(role.getResponsibilities() == null
                        ? List.of()
                        : role.getResponsibilities().stream().map(RoleResponsibility::getResponsibility).collect(Collectors.toList()))
                .createdAt(role.getCreatedAt())
                .updatedAt(role.getUpdatedAt())
                .build();
    }

    private TeamResponse toTeamResponse(Team team) {
        return TeamResponse.builder()
                .id(team.getId())
                .name(team.getName())
                .topology(team.getTopology())
                .createdAt(team.getCreatedAt())
                .updatedAt(team.getUpdatedAt())
                .build();
    }

    public record AgentResponse(UUID id, String roleName, UUID roleId, String teamName, UUID teamId, AgentStatus status, Long tokensUsed,
                                java.time.Instant lastActiveAt, long activeTaskCount) {
    }

    public record MemoryEntryResponse(UUID id, UUID organizationId, UUID agentId, UUID taskId, String content, MemoryScope scope, Integer confidence,
                                      Boolean superseded, java.time.Instant createdAt) {
    }

    public record ApprovalViewResponse(UUID id, UUID taskId, String actionDescription,
                                       com.synapse.core.model.ApprovalEffectType effectType,
                                       Integer confidence, ApprovalStatus status,
                                       String resolvedBy, java.time.Instant resolvedAt,
                                       java.time.Instant createdAt) {
    }
}
