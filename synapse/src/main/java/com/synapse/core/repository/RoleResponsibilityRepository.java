package com.synapse.core.repository;

import com.synapse.core.entity.RoleResponsibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface RoleResponsibilityRepository extends JpaRepository<RoleResponsibility, UUID> {
    List<RoleResponsibility> findByRoleId(UUID roleId);
}
