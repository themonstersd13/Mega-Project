package com.synapse.core.repository;

import com.synapse.core.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {
    List<Message> findByRunId(UUID runId);
    List<Message> findByTraceId(UUID traceId);
    List<Message> findBySenderAgentId(UUID senderAgentId);
    List<Message> findByRecipientAgentId(UUID recipientAgentId);
}
