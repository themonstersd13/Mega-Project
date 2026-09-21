package com.synapse.core.repository;

import com.synapse.core.entity.DeadLetterEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface DeadLetterEntryRepository extends JpaRepository<DeadLetterEntry, UUID> {
    List<DeadLetterEntry> findByTaskId(UUID taskId);
    List<DeadLetterEntry> findByResolved(Boolean resolved);
}
