package com.synapse.core.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Data
@Getter
@Setter
@Builder
@Entity
@Table(name = "role_responsibility", uniqueConstraints = {@UniqueConstraint(columnNames = {"role_id", "responsibility"})})
@NoArgsConstructor
@AllArgsConstructor
public class RoleResponsibility {
    @Id
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(nullable = false)
    private String responsibility;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
