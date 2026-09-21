package com.synapse.core.repository;

import com.synapse.core.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {
    List<Role> findByOrganizationId(UUID organizationId);
    Optional<Role> findByIdAndOrganizationId(UUID id, UUID organizationId);
    Optional<Role> findByNameAndOrganizationId(String name, UUID organizationId);
}
