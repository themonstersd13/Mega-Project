package com.synapse.core.repository;

import com.synapse.core.entity.MemoryEntry;
import com.synapse.core.model.MemoryScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface MemoryEntryRepository extends JpaRepository<MemoryEntry, UUID> {
    List<MemoryEntry> findByOrganizationId(UUID organizationId);
    List<MemoryEntry> findByAgentId(UUID agentId);
    List<MemoryEntry> findByScope(MemoryScope scope);
    List<MemoryEntry> findByOrganizationIdAndScope(UUID organizationId, MemoryScope scope);
    List<MemoryEntry> findByTaskId(UUID taskId);
}
