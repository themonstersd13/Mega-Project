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
import java.util.List;
import java.util.UUID;

@Data
@Getter
@Setter
@Builder
@Entity
@Table(name = "organization")
@NoArgsConstructor
@AllArgsConstructor
public class Organization {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(columnDefinition = "NUMERIC(12, 2)")
    private BigDecimal totalBudgetUsd;

    @Column(columnDefinition = "NUMERIC(12, 2)")
    private BigDecimal spentBudgetUsd;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant createdAt;

    @Column(name = "updated_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant updatedAt;

    @Version
    private Long version;

    @OneToMany(mappedBy = "organization", cascade = CascadeType.ALL)
    private List<Role> roles;

    @OneToMany(mappedBy = "organization", cascade = CascadeType.ALL)
    private List<Team> teams;

    @OneToMany(mappedBy = "organization", cascade = CascadeType.ALL)
    private List<Goal> goals;

    @OneToMany(mappedBy = "organization", cascade = CascadeType.ALL)
    private List<MemoryEntry> memoryEntries;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
        if (spentBudgetUsd == null) spentBudgetUsd = BigDecimal.ZERO;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
