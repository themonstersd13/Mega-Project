package com.synapse.core.repository;

import com.synapse.core.entity.Goal;
import com.synapse.core.model.GoalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GoalRepository extends JpaRepository<Goal, UUID> {
    List<Goal> findByOrganizationId(UUID organizationId);
    List<Goal> findByStatus(GoalStatus status);
    List<Goal> findByOrganizationIdAndStatus(UUID organizationId, GoalStatus status);
    Optional<Goal> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
