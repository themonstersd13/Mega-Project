package com.synapse.core.repository;

import com.synapse.core.entity.Approval;
import com.synapse.core.model.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ApprovalRepository extends JpaRepository<Approval, UUID> {
    List<Approval> findByTaskId(UUID taskId);
    List<Approval> findByStatus(ApprovalStatus status);
}
