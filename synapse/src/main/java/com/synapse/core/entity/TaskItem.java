package com.synapse.core.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import com.synapse.core.model.TaskItemStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Getter
@Setter
@Builder
@Entity
@Table(name = "task_item")
@NoArgsConstructor
@AllArgsConstructor
public class TaskItem {
    @Id
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "goal_id", nullable = false)
    private Goal goal;

    @ManyToOne
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne
    @JoinColumn(name = "parent_task_id")
    private TaskItem parentTask;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "required_skill_tags", columnDefinition = "TEXT[]")
    private String[] requiredSkillTags;

    @Enumerated(EnumType.STRING)
    private TaskItemStatus status;

    @ManyToOne
    @JoinColumn(name = "assigned_agent_id")
    private Agent assignedAgent;

    @Column
    private Integer priority;

    @Column(name = "deadline", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private LocalDateTime deadline;

    @Column(name = "estimated_cost_usd", columnDefinition = "NUMERIC(12, 4)")
    private BigDecimal estimatedCostUsd;

    @Column(name = "estimated_latency_ms")
    private Long estimatedLatencyMs;

    @Column(name = "actual_cost_usd", columnDefinition = "NUMERIC(12, 4)")
    private BigDecimal actualCostUsd;

    @Column(name = "actual_latency_ms")
    private Long actualLatencyMs;

    @Column
    private Integer confidence;

    @Column(name = "retry_count")
    private Integer retryCount;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant createdAt;

    @Column(name = "updated_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant updatedAt;

    @Version
    private Long version;

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL)
    private List<ExecutionLog> executionLogs;

    @OneToMany(mappedBy = "parentTask", cascade = CascadeType.ALL)
    private List<TaskItem> subtasks;

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL)
    private List<MemoryEntry> memoryEntries;

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL)
    private List<Approval> approvals;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
        if (retryCount == null) retryCount = 0;
        if (status == null) status = TaskItemStatus.PENDING;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
