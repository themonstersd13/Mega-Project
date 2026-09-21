package com.synapse.core.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.synapse.core.model.ApprovalEffectType;
import com.synapse.core.model.ApprovalStatus;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@Entity
@Table(name = "approval")
@NoArgsConstructor
@AllArgsConstructor
public class Approval {
    @Id
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "task_id", nullable = false)
    private TaskItem task;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String actionDescription;

    @Enumerated(EnumType.STRING)
    @Column(name = "effect_type")
    private ApprovalEffectType effectType;

    @Column(name = "confidence")
    private Integer confidence;

    @Enumerated(EnumType.STRING)
    private ApprovalStatus status;

    @Column(name = "resolved_by")
    private String resolvedBy;

    @Column(name = "resolved_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant resolvedAt;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
