package com.synapse.core.service;

import com.synapse.core.entity.Agent;
import com.synapse.core.entity.Message;
import com.synapse.core.repository.MessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @Test
    void messageServicePersistsAndRetrievesTraceMessages() {
        UUID runId = UUID.randomUUID();
        UUID traceId = UUID.randomUUID();
        Agent sender = Agent.builder().id(UUID.randomUUID()).build();
        Message message = Message.builder()
                .id(UUID.randomUUID())
                .runId(runId)
                .traceId(traceId)
                .senderAgent(sender)
                .content("Matched task")
                .build();

        when(messageRepository.save(any(Message.class))).thenReturn(message);
        when(messageRepository.findByRunId(runId)).thenReturn(List.of(message));
        when(messageRepository.findByTraceId(traceId)).thenReturn(List.of(message));

        MessageService service = new MessageService(messageRepository);
        Message saved = service.createMessage(runId, traceId, sender, null, "Matched task", 80);

        assertNotNull(saved);
        assertEquals(runId, saved.getRunId());
        assertEquals(traceId, saved.getTraceId());
        assertEquals(1, service.findByRunId(runId).size());
        assertEquals(1, service.findByTraceId(traceId).size());
    }
}
