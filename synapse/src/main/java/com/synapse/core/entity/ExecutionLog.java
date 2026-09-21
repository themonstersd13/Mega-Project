package com.synapse.core.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Getter
@Setter
@Builder
@Entity
@Table(name = "execution_log")
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionLog {
    @Id
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "task_id", nullable = false)
    private TaskItem task;

    @Column(name = "role_name")
    private String roleName;

    @Column(name = "required_skill_tag_count")
    private Integer requiredSkillTagCount;

    @Column(name = "description_length")
    private Integer descriptionLength;

    @Column(name = "model_tier")
    private String modelTier;

    @Column(name = "batch_size_at_execution")
    private Integer batchSizeAtExecution;

    @Column(name = "context_overlap_score", columnDefinition = "NUMERIC(5, 2)")
    private BigDecimal contextOverlapScore;

    @Column(name = "deadline_proximity_minutes")
    private Integer deadlineProximityMinutes;

    @Column(name = "priority")
    private Integer priority;

    @Column(name = "actual_tokens_used")
    private Long actualTokensUsed;

    @Column(name = "actual_cost_usd", columnDefinition = "NUMERIC(12, 4)")
    private BigDecimal actualCostUsd;

    @Column(name = "actual_latency_ms")
    private Long actualLatencyMs;

    @Column(name = "confidence")
    private Integer confidence;

    @Column(name = "was_escalated")
    private Boolean wasEscalated;

    @Column(name = "retry_count")
    private Integer retryCount;

    @Column(name = "succeeded")
    private Boolean succeeded;

    @Column(name = "human_approval_required")
    private Boolean humanApprovalRequired;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
