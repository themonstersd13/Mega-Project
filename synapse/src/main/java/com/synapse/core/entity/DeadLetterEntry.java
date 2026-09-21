package com.synapse.core.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@Entity
@Table(name = "dead_letter_entry")
@NoArgsConstructor
@AllArgsConstructor
public class DeadLetterEntry {
    @Id
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "task_id")
    private TaskItem task;

    @Column(columnDefinition = "TEXT")
    private String originalPrompt;

    @Column(columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "failed_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant failedAt;

    @Column(name = "resolved")
    private Boolean resolved;

    @PrePersist
    protected void onCreate() {
        if (failedAt == null) failedAt = Instant.now();
        if (resolved == null) resolved = false;
    }
}
