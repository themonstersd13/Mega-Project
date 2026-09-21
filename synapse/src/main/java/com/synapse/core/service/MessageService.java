package com.synapse.core.service;

import com.synapse.core.entity.Agent;
import com.synapse.core.entity.Message;
import com.synapse.core.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;

    public Message createMessage(
            UUID runId,
            UUID traceId,
            Agent senderAgent,
            Agent recipientAgent,
            String content,
            Integer confidence) {
        return createMessage(null, runId, traceId, senderAgent, recipientAgent, content, confidence);
    }

    public Message createMessage(
            Message parentMessage,
            UUID runId,
            UUID traceId,
            Agent senderAgent,
            Agent recipientAgent,
            String content,
            Integer confidence) {
        Message message = Message.builder()
                .id(UUID.randomUUID())
                .parentMessage(parentMessage)
                .runId(runId)
                .traceId(traceId)
                .senderAgent(senderAgent)
                .recipientAgent(recipientAgent)
                .content(content)
                .confidence(confidence)
                .build();

        return messageRepository.save(message);
    }

    public List<Message> findByRunId(UUID runId) {
        return messageRepository.findByRunId(runId);
    }

    public List<Message> findByTraceId(UUID traceId) {
        return messageRepository.findByTraceId(traceId);
    }
}
