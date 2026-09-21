package com.synapse.core.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import com.synapse.core.model.ModelTier;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Data
@Getter
@Setter
@Builder
@Entity
@Table(name = "role", uniqueConstraints = {@UniqueConstraint(columnNames = {"organization_id", "name"})})
@NoArgsConstructor
@AllArgsConstructor
public class Role {
    @Id
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String systemPrompt;

    @Enumerated(EnumType.STRING)
    private ModelTier modelTier;

    @Column(name = "token_budget")
    private Long tokenBudget;

    @ManyToOne
    @JoinColumn(name = "escalates_to_role_id")
    private Role escalatesTo;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant createdAt;

    @Column(name = "updated_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant updatedAt;

    @Version
    private Long version;

    @OneToMany(mappedBy = "role", cascade = CascadeType.ALL)
    private List<Agent> agents;

    @OneToMany(mappedBy = "role", cascade = CascadeType.ALL)
    private Set<RoleResponsibility> responsibilities;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
