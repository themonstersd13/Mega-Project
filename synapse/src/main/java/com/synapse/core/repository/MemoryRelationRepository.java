package com.synapse.core.repository;

import com.synapse.core.entity.MemoryRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface MemoryRelationRepository extends JpaRepository<MemoryRelation, UUID> {
    List<MemoryRelation> findByFromMemoryId(UUID fromMemoryId);
    List<MemoryRelation> findByToMemoryId(UUID toMemoryId);
}
