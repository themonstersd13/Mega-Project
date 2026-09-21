package com.synapse.core.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.synapse.core.model.MemoryRelationType;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@Entity
@Table(name = "memory_relation")
@NoArgsConstructor
@AllArgsConstructor
public class MemoryRelation {
    @Id
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "from_memory_id", nullable = false)
    private MemoryEntry fromMemory;

    @ManyToOne
    @JoinColumn(name = "to_memory_id", nullable = false)
    private MemoryEntry toMemory;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation_type")
    private MemoryRelationType relationType;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
