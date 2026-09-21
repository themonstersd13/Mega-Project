package com.synapse.core.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import com.synapse.core.model.AgentStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Getter
@Setter
@Builder
@Entity
@Table(name = "agent")
@NoArgsConstructor
@AllArgsConstructor
public class Agent {
    @Id
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @ManyToOne
    @JoinColumn(name = "team_id")
    private Team team;

    @Enumerated(EnumType.STRING)
    private AgentStatus status;

    @Column(name = "tokens_used")
    private Long tokensUsed;

    @Column(name = "last_active_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant lastActiveAt;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant createdAt;

    @Column(name = "updated_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant updatedAt;

    @Version
    private Long version;

    @OneToMany(mappedBy = "assignedAgent", cascade = CascadeType.ALL)
    private List<TaskItem> assignedTasks;

    @OneToMany(mappedBy = "agent", cascade = CascadeType.ALL)
    private List<MemoryEntry> memoryEntries;

    @OneToMany(mappedBy = "senderAgent", cascade = CascadeType.ALL)
    private List<Message> sentMessages;

    @OneToMany(mappedBy = "recipientAgent", cascade = CascadeType.ALL)
    private List<Message> receivedMessages;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
        if (tokensUsed == null) tokensUsed = 0L;
        if (status == null) status = AgentStatus.IDLE;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
